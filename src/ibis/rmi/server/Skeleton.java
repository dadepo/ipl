package ibis.rmi.server;

import ibis.ipl.ReadMessage;
import ibis.ipl.Upcall;
import ibis.ipl.ReceivePortConnectUpcall;
import ibis.ipl.SendPort;
import ibis.ipl.ReceivePort;
import ibis.ipl.ReceivePortIdentifier;
import ibis.ipl.SendPortIdentifier;

import ibis.rmi.RTS;

import java.util.Vector;

import java.io.IOException;

public abstract class Skeleton implements Upcall {

    public int skeletonId;
    public Object destination;
    public SendPort[] stubs;
    public String stubType;
    private int num_ports = 0;
    private int max_ports = 0;
    private int counter = 0;

    private static final int INCR = 16;

    public Skeleton() {
	stubs = new SendPort[INCR];
	max_ports = INCR;
    }

    public void init(int id, Object o) { 
	skeletonId = id;
	destination = o;
    }    

    protected void finalize() {
	cleanup();
    }

    public synchronized int addStub(ReceivePortIdentifier rpi) { 
	try { 
	    int id = 0;
	    SendPort s = RTS.getSendPort(rpi);

	    for (int i = 0; i <= num_ports; i++) {
		if (stubs[i] == s) {
		    id = i;
		    break;
		}
	    }

	    if (id == 0) {
		id = ++num_ports;
		if (id >= max_ports) {
		    SendPort[] newports = new SendPort[max_ports+INCR];
		    for (int i = 0; i < max_ports; i++) newports[i] = stubs[i];
		    max_ports += INCR;
		    stubs = newports;
		}
		stubs[id] = s;
	    }

	    return id;
	} catch (Exception e) { 
	    System.out.println("OOPS " + e);
	    e.printStackTrace();
	    System.exit(1);			
	} 
	return -1; 
    }

    private void cleanup() {
	destination = null;
	for (int i = 0; i < stubs.length; i++) {
	    if (stubs[i] != null) {
		try {
		    /* stubs[i].free(); */
		    stubs[i] = null;
		} catch(Exception e) {
		}
	    }
	}
    }

    public abstract void upcall(ReadMessage m) throws IOException;
} 
