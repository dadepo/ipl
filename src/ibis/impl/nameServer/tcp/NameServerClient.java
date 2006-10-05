/* $Id$ */

package ibis.impl.nameServer.tcp;

import ibis.connect.direct.SocketAddressSet;
import ibis.connect.virtual.*;

import ibis.impl.nameServer.NSProps;
import ibis.io.Conversion;
import ibis.ipl.ConnectionRefusedException;
import ibis.ipl.ConnectionTimedOutException;
import ibis.ipl.Ibis;
import ibis.ipl.IbisConfigurationException;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.IbisRuntimeException;
import ibis.ipl.ReceivePortIdentifier;
import ibis.ipl.StaticProperties;
import ibis.util.RunProcess;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import org.apache.log4j.Logger;

public class NameServerClient extends ibis.impl.nameServer.NameServer
        implements Runnable, Protocol {

    private PortTypeNameServerClient portTypeNameServerClient;

    private ReceivePortNameServerClient receivePortNameServerClient;

    private ElectionClient electionClient;

    private VirtualServerSocket serverSocket;

    private Ibis ibisImpl;

    private boolean needsUpcalls;

    private IbisIdentifier id;

    private volatile boolean stop = false;

    private VirtualSocketAddress serverAddress;
    
    private String poolName;

    private VirtualSocketAddress myAddress;

    private boolean left = false;

    private static VirtualSocketFactory socketFactory; 
    
    static { 
        HashMap properties = new HashMap();        
        properties.put("modules.direct.port", "16789");                
        socketFactory = VirtualSocketFactory.getSocketFactory(properties, true);
    } 

    private static Logger logger
            = ibis.util.GetLogger.getLogger(NameServerClient.class.getName());

    static VirtualSocket nsConnect(VirtualSocketAddress dest, boolean verbose, 
            int timeout) throws IOException {
        
        // TODO: fix timeout
        
        VirtualSocket s = null;
        int cnt = 0;
        while (s == null) {
            try {
                cnt++;
                s = socketFactory.createClientSocket(dest, 1000, null);
            } catch (IOException e) {
                if (cnt == timeout) {
                    if (verbose) {
                        logger.error("Nameserver client failed"
                                + " to connect to nameserver\n at "
                                + dest);
                        logger.error("Gave up after " + timeout
                                + " seconds");
                    }
                    throw new ConnectionTimedOutException(e);
                }
                if (cnt == 10 && verbose) {
                    // Rather arbitrary, 10 seconds, print warning
                    logger.error("Nameserver client failed"
                            + " to connect to nameserver\n at " + dest
                            + ", will keep trying");
                } 
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e2) {
                    // don't care
                }
            }
        }
        return s;
    }

    public NameServerClient() {
        /* do nothing */
    }

    private void writeVirtualSocketAddress(DataOutputStream out, 
            VirtualSocketAddress a) throws IOException {
        /*
        byte [] buf = Conversion.object2byte(a);
        out.writeInt(buf.length);
        out.write(buf);
        */
        
        out.writeUTF(a.toString());
    }
    
    private VirtualSocketAddress readVirtualSocketAddress(DataInputStream in) throws IOException {
/*
        int len = in.readInt();
        byte[] buf = new byte[len];
        in.readFully(buf, 0, len);

        try {
            return (VirtualSocketAddress) Conversion.byte2object(buf);
        } catch(ClassNotFoundException e) {
            throw new IOException("Could not read InetAddress");
        }
        */
        
        return new VirtualSocketAddress(in.readUTF());
    }
    
    void runNameServer(int prt, String srvr) {
        if (System.getProperty("os.name").matches(".*indows.*")) {
            // The code below does not work for windows2000, don't know why ...
            NameServer n = NameServer.createNameServer(true, false, false,
                    false, true);

            if (n != null) {
                n.setDaemon(true);
                n.start();
            }
        } else {
            // Start the nameserver in a separate jvm, so that it can keep
            // on running if this particular ibis instance dies.
            String javadir = System.getProperty("java.home");
            String javapath = System.getProperty("java.class.path");
            String filesep = System.getProperty("file.separator");
            String pathsep = System.getProperty("path.separator");

            if (javadir.endsWith("jre")) {
                javadir = javadir.substring(0, javadir.length()-4);
            }

            String conn = System.getProperty("ibis.connect.control_links");
            String hubhost = null;
            String hubport = null;
            if (conn != null && conn.equals("RoutedMessages")) {
                hubhost = System.getProperty("ibis.connect.hub.host");
                if (hubhost == null) {
                    hubhost = srvr;
                }
                hubport = System.getProperty("ibis.connect.hub.port");
            }

            ArrayList command = new ArrayList();
            command.add(javadir + filesep + "bin" + filesep + "java");
            command.add("-classpath");
            command.add(javapath + pathsep);
            command.add("-Dibis.name_server.port="+prt);
            if (hubhost != null && ! srvr.equals(hubhost)) {
                command.add("-Dibis.connect.hub.host=" + hubhost);
            }
            if (hubport != null) {
                command.add("-Dibis.connect.hub.port=" + hubport);
            }
            command.add("ibis.impl.nameServer.tcp.NameServer");
            command.add("-single");
            command.add("-no-retry");
            command.add("-silent");
            command.add("-no-poolserver");

            if (hubhost != null && hubhost.equals(srvr)) {
                command.add("-controlhub");
            }
                        
            final String[] cmd = (String[]) command.toArray(new String[0]);

            Thread p = new Thread("NameServer starter") {
                public void run() {
                    RunProcess r = new RunProcess(cmd, new String[0]);
                    byte[] err = r.getStderr();
                    byte[] out = r.getStdout();
                    if (out.length != 0) {
                        System.out.write(out, 0, out.length);
                        System.out.println("");
                    }
                    if (err.length != 0) {
                        System.err.write(err, 0, err.length);
                        System.err.println("");
                    }
                }
            };

            p.setDaemon(true);
            p.start();
        }
    }

    protected void init(Ibis ibis, boolean ndsUpcalls)
            throws IOException, IbisConfigurationException {
        this.ibisImpl = ibis;
        this.id = ibisImpl.identifier();
        this.needsUpcalls = ndsUpcalls;

        Properties p = System.getProperties();

        String server = p.getProperty(NSProps.s_host);
        
        if (server == null) {
            throw new IbisConfigurationException(
                    "property ibis.name_server.host is not specified");
        }
        
        serverSocket = socketFactory.createServerSocket(0, 50, true, null);
        myAddress = serverSocket.getLocalSocketAddress();

        int port = NameServer.TCP_IBIS_NAME_SERVER_PORT_NR;
        
        boolean containsColon = (server.lastIndexOf(':') >= 0);
        
        if (!containsColon) { 
            // no port number present, so check if it's provided seperately
            String nameServerPortString = p.getProperty(NSProps.s_port);
            
            if (nameServerPortString != null) {
                try {
                    port = Integer.parseInt(nameServerPortString);
                    logger.debug("Using nameserver port: " + port);
                } catch (Exception e) {
                    System.err.println("illegal nameserver port: "
                            + nameServerPortString + ", using default");
                }
            }                        
        } 
                            
        if (server.equals("localhost")) {            
            // We want to start the server on this machine.
            // TODO: Fix!!
            runNameServer(port, "bla");            
                        
            serverAddress = new VirtualSocketAddress(
                new SocketAddressSet(myAddress.machine().getAddressSet(), port), 
                port);
        } else {                     
            if (containsColon) { 
                // Assume "server" uses the IP/IP:PORT/IP:PORT format
                serverAddress = new VirtualSocketAddress(server);            
            } else {
                // Assume "server" uses the IP/IP/IP format                 
                serverAddress = new VirtualSocketAddress(server, port);
            } 
        }

        logger.debug("Found nameServerInet " + serverAddress);

        // Next, get the nameserver key ....
        poolName = p.getProperty(NSProps.s_key);
        if (poolName == null) {
            throw new IbisConfigurationException(
                    "property ibis.name_server.key is not specified");
        }
                        
        // Then connect to the nameserver
        VirtualSocket s = nsConnect(serverAddress, true, 60);

        /*
        String nameServerPortString = p.getProperty(NSProps.s_port);
        port = NameServer.TCP_IBIS_NAME_SERVER_PORT_NR;
        if (nameServerPortString != null) {
            try {
                port = Integer.parseInt(nameServerPortString);
                logger.debug("Using nameserver port: " + port);
            } catch (Exception e) {
                logger.error("illegal nameserver port: "
                        + nameServerPortString + ", using default");
            }
        }

        serverAddress = InetAddress.getByName(server);
        // serverAddress.getHostName();

        if (myAddress.equals(serverAddress)) {
            // Try and start a nameserver ...
            runNameServer(port, server);
        }

        logger.debug("Found nameServerInet " + serverAddress);

        serverSocket = socketFactory.createServerSocket(0, myAddress, 50, true, null);

        localPort = serverSocket.getLocalPort();

        Socket s = nsConnect(serverAddress, port, myAddress, true, 60);
        */
 
        DataOutputStream out = null;
        DataInputStream in = null;

        try {
            out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));

            logger.debug("NameServerClient: contacting nameserver");
            out.writeByte(IBIS_JOIN);
            out.writeUTF(poolName);
            out.writeUTF(id.name());
            
            byte[] buf = Conversion.object2byte(id);
            out.writeInt(buf.length);
            out.write(buf);
            
            writeVirtualSocketAddress(out, myAddress);

            out.writeBoolean(ndsUpcalls);

            out.flush();

            in = new DataInputStream(new BufferedInputStream(s.getInputStream()));

            int opcode = in.readByte();

            if (logger.isDebugEnabled()) {
                logger.debug("NameServerClient: nameserver reply, opcode "
                        + opcode);
            }

            switch (opcode) {
            case IBIS_REFUSED:
                throw new ConnectionRefusedException("NameServerClient: "
                        + id + " is not unique!");
            case IBIS_ACCEPTED:
                // read the ports for the other name servers and start the
                // receiver thread...
                
                /* Address for the PortTypeNameServer */
                portTypeNameServerClient = new PortTypeNameServerClient(
                        readVirtualSocketAddress(in), id.name());

                /* Address for the ReceivePortNameServer */
                receivePortNameServerClient = new ReceivePortNameServerClient(
                        readVirtualSocketAddress(in), id.name(), 
                        serverSocket.getLocalSocketAddress());

                /* Address for the ElectionServer */
                electionClient = new ElectionClient(
                        readVirtualSocketAddress(in), id.name());

                if (ndsUpcalls) {
                    int poolSize = in.readInt();

                    if (logger.isDebugEnabled()) {
                        logger.debug("NameServerClient: accepted by nameserver, "
                                    + "poolsize " + poolSize);
                    }

                    // Read existing nodes (including this one).
                    for (int i = 0; i < poolSize; i++) {
                        IbisIdentifier newid;
                        try {
                            int len = in.readInt();
                            byte[] b = new byte[len];
                            in.readFully(b, 0, len);
                            newid = (IbisIdentifier) Conversion.byte2object(b);
                        } catch (ClassNotFoundException e) {
                            throw new IOException("Receive IbisIdent of unknown class "
                                    + e);
                        }
                        if (logger.isDebugEnabled()) {
                            logger.debug("NameServerClient: join of " + newid);
                        }
                        ibisImpl.joined(newid);
                        if (logger.isDebugEnabled()) {
                            logger.debug("NameServerClient: join of " + newid
                                    + " DONE");
                        }
                    }

                    // at least read the tobedeleted stuff!
                    int tmp = in.readInt();
                    for (int i = 0; i < tmp; i++) {
                        int len = in.readInt();
                        byte[] b = new byte[len];
                        in.readFully(b, 0, len);
                    }
                }

                Thread t = new Thread(this, "NameServerClient accept thread");
                t.setDaemon(true);
                t.start();
                break;
            default:
                throw new StreamCorruptedException(
                        "NameServerClient: got illegal opcode " + opcode);
            }
        } finally {
            NameServer.closeConnection(in, out, s);
        }
    }

    public void maybeDead(IbisIdentifier ibisId) throws IOException {
        VirtualSocket s = null;
        DataOutputStream out = null;

        try {
            logger.debug("Sending maybeDead(" + ibisId + ") to nameserver");
            s = socketFactory.createClientSocket(serverAddress, 5000, null);

            logger.debug("connection setup done");
                        
            out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));

            out.writeByte(IBIS_ISALIVE);
            out.writeUTF(poolName);
            out.writeUTF(ibisId.name());
            out.flush();
            
            logger.debug("NS client: isAlive sent");

        } catch (ConnectionTimedOutException e) {            
            logger.warn("Could not contact nameserver!!");                       
            // Apparently, the nameserver left. Assume dead.
            return;
        } finally {
            NameServer.closeConnection(null, out, s);
        }
    }

    public void dead(IbisIdentifier corpse) throws IOException {
        VirtualSocket s = null;
        DataOutputStream out = null;

        try {
            s = socketFactory.createClientSocket(serverAddress, -1, null);

            out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));

            out.writeByte(IBIS_DEAD);
            out.writeUTF(poolName);
            out.writeUTF(corpse.name());
            logger.debug("NS client: kill sent");
        } catch (ConnectionTimedOutException e) {
            return;
        } finally {
            NameServer.closeConnection(null, out, s);
        }
    }

    public void mustLeave(IbisIdentifier[] ibisses) throws IOException {
        VirtualSocket s = null;
        DataOutputStream out = null;

        try {
            s = socketFactory.createClientSocket(serverAddress, 0, null);

            out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));

            out.writeByte(IBIS_MUSTLEAVE);
            out.writeUTF(poolName);
            out.writeInt(ibisses.length);
            for (int i = 0; i < ibisses.length; i++) {
                out.writeUTF(ibisses[i].name());
            }
            logger.debug("NS client: mustLeave sent");
        } catch (ConnectionTimedOutException e) {
            return;
        } finally {
            NameServer.closeConnection(null, out, s);
        }
    }

    public boolean newPortType(String name, StaticProperties p)
            throws IOException {
        return portTypeNameServerClient.newPortType(name, p);
    }

    public long getSeqno(String name) throws IOException {
        return portTypeNameServerClient.getSeqno(name);
    }

    public void leave() {
        VirtualSocket s = null;
        DataOutputStream out = null;
        DataInputStream in = null;

        logger.info("NS client: leave");

        try {
            s = socketFactory.createClientSocket(serverAddress, 5000, null);
        } catch (IOException e) {
            if (logger.isInfoEnabled()) {
                logger.info("leave: connect got exception", e);
            }
            return;
        }

        try {
            out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));

            out.writeByte(IBIS_LEAVE);
            out.writeUTF(poolName);
            out.writeUTF(id.name());
            out.flush();
            logger.info("NS client: leave sent");

            in = new DataInputStream(new BufferedInputStream(s.getInputStream()));

            in.readByte();
            logger.info("NS client: leave ack received");
        } catch (IOException e) {
            logger.info("leave got exception", e);
        } finally {
            NameServer.closeConnection(in, out, s);
        }

        if (! needsUpcalls) {
            synchronized (this) {
                stop = true;
            }
        } else {
            synchronized(this) {
                while (! left) {
                    try {
                        wait();
                    } catch(Exception e) {
                        // Ignored
                    }
                }
            }
        }

        logger.info("NS client: leave DONE");
    }

    public void run() {
        logger.info("NameServerClient: thread started");

        while (! stop) {

            VirtualSocket s;
            IbisIdentifier ibisId;

            try {
                s = serverSocket.accept();

                logger.debug("NameServerClient: incoming connection "
                        + "from " + s.toString());

            } catch (Exception e) {
                if (stop) {
                    logger.info("NameServerClient: thread dying");
                    try {
                        serverSocket.close();
                    } catch (IOException e1) {
                        /* do nothing */
                    }
                    break;
                }
                if (needsUpcalls) {
                    synchronized(this) {
                        stop = true;
                        left = true;
                        notifyAll();
                    }
                }
                throw new IbisRuntimeException(
                        "NameServerClient: got an error", e);
            }

            byte opcode = 0;
            DataInputStream in = null;
            DataOutputStream out = null;

            try {
                in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                int count;
                IbisIdentifier[] ids;

                opcode = in.readByte();
                logger.debug("NameServerClient: opcode " + opcode);

                switch (opcode) {
                case (IBIS_PING):
                    {
                        out = new DataOutputStream(
                                new BufferedOutputStream(s.getOutputStream()));
                        out.writeUTF(poolName);
                        out.writeUTF(id.name());
                    }
                    break;

                case (IBIS_JOIN):
                    
                    if (logger.isDebugEnabled()) {
                        logger.debug("NameServerClient: receiving join ");
                    }
                                        
                    count = in.readInt();
                    for (int i = 0; i < count; i++) {
                        int sz = in.readInt();
                        byte[] buf = new byte[sz];
                        in.readFully(buf, 0, sz);
                        ibisId = (IbisIdentifier) Conversion.byte2object(buf);
                        logger.debug("NameServerClient: receive join request "
                            + ibisId);
                        ibisImpl.joined(ibisId);
                    }
                    
                    if (logger.isDebugEnabled()) {
                        logger.debug("NameServerClient: join done");
                    }
                    
                    break;

                case (IBIS_LEAVE):
                    count = in.readInt();
                    for (int i = 0; i < count; i++) {
                        int sz = in.readInt();
                        byte[] buf = new byte[sz];
                        in.readFully(buf, 0, sz);
                        ibisId = (IbisIdentifier) Conversion.byte2object(buf);
                        if (ibisId.equals(this.id)) {
                            // received an ack from the nameserver that I left.
                            logger.info("NameServerClient: thread dying");
                            stop = true;
                        } else {
                            ibisImpl.left(ibisId);
                        }
                    }
                    if (stop) {
                        synchronized(this) {
                            left = true;
                            notifyAll();
                        }
                    }
                    break;

                case (IBIS_DEAD):
                    count = in.readInt();
                    ids = new IbisIdentifier[count];
                    for (int i = 0; i < count; i++) {
                        int sz = in.readInt();
                        byte[] buf = new byte[sz];
                        in.readFully(buf, 0, sz);
                        ids[i] = (IbisIdentifier) Conversion.byte2object(buf);
                    }

                    ibisImpl.died(ids);
                    break;

                case (IBIS_MUSTLEAVE):
                    count = in.readInt();
                    ids = new IbisIdentifier[count];
                    for (int i = 0; i < count; i++) {
                        int sz = in.readInt();
                        byte[] buf = new byte[sz];
                        in.readFully(buf, 0, sz);
                        ids[i] = (IbisIdentifier) Conversion.byte2object(buf);
                    }

                    ibisImpl.mustLeave(ids);
                    break;

                case PORT_KNOWN:
                case PORT_UNKNOWN:
                    receivePortNameServerClient.gotAnswer(opcode, in);
                    break;

                default:
                    logger.error("NameServerClient: got an illegal "
                            + "opcode " + opcode);
                }
            } catch (Exception e1) {
                if (! stop) {
                    logger.error("Got an exception in "
                            + "NameServerClient.run "
                            + "(opcode = " + opcode + ")", e1);
                }
            } finally {
                NameServer.closeConnection(in, out, s);
            }
        }
        logger.debug("NameServerClient: thread stopped");
    }

    public ReceivePortIdentifier lookupReceivePort(String name)
            throws IOException {
        return lookupReceivePort(name, 0);
    }

    public ReceivePortIdentifier lookupReceivePort(String name, long timeout)
            throws IOException {
        return receivePortNameServerClient.lookup(name, timeout);
    }

    public ReceivePortIdentifier[] lookupReceivePorts(String[] names)
            throws IOException {
        return lookupReceivePorts(names, 0, false);
    }

    public ReceivePortIdentifier[] lookupReceivePorts(String[] names, 
            long timeout, boolean allowPartialResult)
            throws IOException {
        return receivePortNameServerClient.lookup(names, timeout, 
                allowPartialResult);
    }

    public IbisIdentifier lookupIbis(String name) {
        return lookupIbis(name, 0);
    }

    public IbisIdentifier lookupIbis(String name, long timeout) {
        /* not implemented yet */
        return null;
    }

    public ReceivePortIdentifier[] listReceivePorts(IbisIdentifier ident) {
        /* not implemented yet */
        return new ReceivePortIdentifier[0];
    }

    public IbisIdentifier elect(String election) throws IOException,
            ClassNotFoundException {
        return electionClient.elect(election, id);
    }

    public IbisIdentifier getElectionResult(String election)
            throws IOException, ClassNotFoundException {
        return electionClient.elect(election, null);
    }

    //gosia	

    public void bind(String name, ReceivePortIdentifier rpi)
            throws IOException {
        receivePortNameServerClient.bind(name, rpi);
    }

    public void rebind(String name, ReceivePortIdentifier rpi)
            throws IOException {
        receivePortNameServerClient.rebind(name, rpi);
    }

    public void unbind(String name) throws IOException {
        receivePortNameServerClient.unbind(name);
    }

    public String[] listNames(String pattern) throws IOException {
        return receivePortNameServerClient.list(pattern);
    }
    //end gosia
}
