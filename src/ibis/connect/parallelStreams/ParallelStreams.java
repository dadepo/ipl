package ibis.connect.parallelStreams;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;

import java.util.Hashtable;

import ibis.connect.socketFactory.ExtSocketFactory;
import ibis.connect.util.MyDebug;

public class ParallelStreams
{
    public static final int defaultNumWays = 4;
    public static final int defaultBlockSize = 1460;
    private int             numWays   = -1;
    private int             blockSize = -1;
    private Socket[]        sockets   = null;
    private InputStream[]   ins       = null;
    private OutputStream[]  outs      = null;

    private int sendPos = 0;
    private int sendBlock = 0;
    private int recvPos = 0;
    private int recvBlock = 0;

    public ParallelStreams(int n, int b)
    {
	if(MyDebug.VERBOSE()) {
	    System.out.println("# ParallelStreams: building link- numWays = "+n+"; blockSize = "+b);
	}
	numWays = n;
	blockSize = b;
	sockets = new Socket[n];
	ins  = new InputStream[n];
	outs = new OutputStream[n];
    }

    public void connect(InputStream in, OutputStream out, boolean hint)
	throws IOException
    {
	int i;

	ObjectOutputStream os = new ObjectOutputStream(out);
	Hashtable lInfo = new Hashtable();
	lInfo.put("num_ways", new Integer(numWays));
	os.writeObject(lInfo);
	os.flush();

	ObjectInputStream is = new ObjectInputStream(in);
	Hashtable rInfo = null;
	try {
	    rInfo = (Hashtable)is.readObject();
	} catch (ClassNotFoundException e) {
	    throw new Error(e);
	}
	MyDebug.out.println("PS: received.");
	int rNumWays = ((Integer) rInfo.get("num_ways")).intValue();
	if(rNumWays != numWays) {
	    throw new Error("ParallelStreams: cannot connect- localNumWays = "+numWays+"; remoteNumWays = "+rNumWays);
	}

	for(i=0; i<numWays; i++) {
	    Socket s = ExtSocketFactory.createBrokeredSocket(in, out, hint);;
	    MyDebug.out.println("PS: done "+i);
	    sockets[i] = s;
	    ins[i] = s.getInputStream();
	    outs[i] = s.getOutputStream();
	}
	//	os.close();
	//	is.close();

    }

    public synchronized int poll()
	throws IOException
    {
	int rc = ins[recvBlock].available();
	MyDebug.out.println("PS: poll()- rc = "+rc);
	// AD: TODO- should be a little bit expanded to be more accurate :-)
	return rc;
    }

    public synchronized int recv(byte[] b, int off, int len)
	throws IOException
    {
	MyDebug.out.println("PS: recv()- len = "+len);
	int nextAvail = 0;
	int done = 0;
	do {
	    int nextRead = Math.min(len, blockSize - recvPos);
	    int rc = ins[recvBlock].read(b, off, nextRead);
	    done += rc;
	    off  += rc;
	    len  -= rc;
	    recvPos = (recvPos + rc) % blockSize;
	    if(recvPos == 0) {
		recvBlock = (recvBlock + 1) % numWays;
	    }
	    nextAvail = ins[recvBlock].available();
	    if(rc < nextRead || nextAvail == 0)
		break;
	    //	} while(nextAvail > 0 && len > 0);
	} while(len > 0);
	MyDebug.out.println("PS: recv()- done = "+done);
	return done;
    }

    public synchronized void send(byte[] b, int off, int len)
	throws IOException
    {
	MyDebug.out.println("PS: send()- len = "+len);
	while(len > 0) {
	    int l = Math.min(len, blockSize - sendPos);
	    outs[sendBlock].write(b, off, l);
	    outs[sendBlock].flush();
	    off += l;
	    len -= l;
	    sendPos = (sendPos + l) % blockSize;
	    if(sendPos == 0) {
		sendBlock = (sendBlock + 1) % numWays;
	    }
	}
    }

    public void close()
	throws IOException
    {
	MyDebug.out.println("PS: close()");
	for(int i=0; i< numWays; i++)
	    {
		ins[i].close();
		outs[i].close();
		sockets[i].close();
	    }
	MyDebug.out.println("PS: close()- ok.");
    }
}
