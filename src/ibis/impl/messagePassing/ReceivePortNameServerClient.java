package ibis.ipl.impl.messagePassing;

import ibis.ipl.IbisIOException;
import ibis.ipl.impl.generic.ConditionVariable;

final class ReceivePortNameServerClient
    implements ReceivePortNameServerProtocol {

    static {
	if (ReceivePortNameServerProtocol.DEBUG) {
	    if (Ibis.myIbis.myCpu == 0) {
		System.err.println("Turn on ReceivePortNS.DEBUG");
	    }
	}
    }


    class Bind implements PollClient {

	PollClient next;
	PollClient prev;

	public PollClient next() {
	    return next;
	}

	public PollClient prev() {
	    return prev;
	}

	public void setNext(PollClient c) {
	    next = c;
	}

	public void setPrev(PollClient c) {
	    prev = c;
	}

	public boolean satisfied() {
	    return bound;
	}

	public void wakeup() {
	    ns_done.cv_signal();
	}

	public void poll_wait(long timeout) {
	    try {
		ns_done.cv_wait(timeout);
	    } catch (InterruptedException e) {
		// ignore
	    }
	}

	Thread me;

	public Thread thread() {
	    return me;
	}

	public void setThread(Thread thread) {
	    me = thread;
	}

	boolean	ns_busy = false;
	ConditionVariable	ns_free = Ibis.myIbis.createCV();

	boolean	ns_is_done;
	ConditionVariable	ns_done = Ibis.myIbis.createCV();
	boolean bound;

	void bind(String name, ReceivePortIdentifier id) throws IbisIOException {

	    if (ReceivePortNameServerProtocol.DEBUG) {
		System.err.println("Try to bind ReceivePortId " + id + " ibis " + id.ibis().name());
	    }

//	    if (! name.equals(id.name)) {
//		System.out.println("name = " + name);
//		System.out.println("id.name = " + id.name);
//		throw new IbisIOException("Corrupted ReceivePort name");
//	    }

	    // request a new Port.
	    Ibis.myIbis.checkLockNotOwned();

	    Ibis.myIbis.lock();
	    try {
		while (ns_busy) {
		    try {
			ns_free.cv_wait();
		    } catch (InterruptedException e) {
			// ignore
		    }
		}
		ns_busy = true;

		bound = false;
		if (ReceivePortNameServerProtocol.DEBUG) {
		    System.err.println(Thread.currentThread() + "Call this rp-ns bind() \"" + name + "\"");
		    Thread.dumpStack();
		}

		ns_bind(name, id.getSerialForm());
// System.err.println(Thread.currentThread() + "Called this rp-ns bind()" + this);

// System.err.println(Thread.currentThread() + "ReceivePortNSClient: Wait for my bind reply");
		if (ReceivePortNameServerProtocol.DEBUG) {
		    if (bound) {
			System.err.println("******** Reply arrives early, bind=" + this);
		    }
		}
		Ibis.myIbis.waitPolling(this, 0, Poll.PREEMPTIVE);
		if (ReceivePortNameServerProtocol.DEBUG) {
		    System.err.println(Thread.currentThread() + "Bind reply arrived, client woken up" + this);
		}

		ns_busy = false;
		ns_free.cv_signal();
	    } finally {
		Ibis.myIbis.unlock();
	    }
	}

    }

    /* Called from native */
    private void bind_reply() {
	Ibis.myIbis.checkLockOwned();
	if (ReceivePortNameServerProtocol.DEBUG) {
	    System.err.println(Thread.currentThread() + "Bind reply arrives, signal client" + this + " bind = " + bind);
	}
	bind.bound = true;
	bind.ns_done.cv_signal();
    }

    native void ns_bind(String name, byte[] recvPortId);

    Bind bind = new Bind();

    public void bind(String name, ReceivePortIdentifier id) throws IbisIOException {
	bind.bind(name, id);
    }


    class Lookup implements PollClient {

	PollClient next;
	PollClient prev;

	public PollClient next() {
	    return next;
	}

	public PollClient prev() {
	    return prev;
	}

	public void setNext(PollClient c) {
	    next = c;
	}

	public void setPrev(PollClient c) {
	    prev = c;
	}

	public boolean satisfied() {
	    return ri != null;
	}

	public void wakeup() {
	    ns_done.cv_signal();
// System.err.println(Thread.currentThread() + "ReceivePortNSClient: signal waiter" + this);
	}

	public void poll_wait(long timeout) {
	    try {
		ns_done.cv_wait(timeout);
	    } catch (InterruptedException e) {
		// ignore
	    }
	}

	Thread me;

	public Thread thread() {
	    return me;
	}

	public void setThread(Thread thread) {
	    me = thread;
	}

	boolean	ns_busy = false;
	ConditionVariable	ns_free = Ibis.myIbis.createCV();

	boolean	ns_is_done;
	ConditionVariable	ns_done = Ibis.myIbis.createCV();
	ReceivePortIdentifier ri;

	private static final int BACKOFF_MILLIS = 1000;

	public ibis.ipl.ReceivePortIdentifier lookup(String name, long timeout) throws IbisIOException {

	    if (ReceivePortNameServerProtocol.DEBUG) {
		System.err.println(Thread.currentThread() + "Lookup receive port \"" + name + "\"");
	    }
// System.err.println(Thread.currentThread() + "ReceivePortNSClient: grab Ibis lock.....");

	    Ibis.myIbis.lock();
	    while (ns_busy) {
// System.err.println(Thread.currentThread() + "ReceivePortNSClient: Wait until the previous client is finished" + this);
		try {
		    ns_free.cv_wait();
		} catch (InterruptedException e) {
		    // ignore
		}
	    }
// System.err.println(Thread.currentThread() + "ReceivePortNSClient: set lookup.ns_busy" + this);
	    ns_busy = true;
	    Ibis.myIbis.unlock();

	    long start = System.currentTimeMillis();
	    long last_try = start - BACKOFF_MILLIS;
	    while (true) {
		long now = System.currentTimeMillis();
		if (timeout > 0 && now - start > timeout) {
		    Ibis.myIbis.lock();
		    ns_busy = false;
		    ns_free.cv_signal();
		    Ibis.myIbis.unlock();
		    throw new IbisIOException("messagePassing.Ibis ReceivePort lookup failed");
		}
		if (now - last_try >= BACKOFF_MILLIS) {
		    Ibis.myIbis.lock();
		    if (ReceivePortNameServerProtocol.DEBUG) System.err.println("Got lock ...");
		    try {
			ri = null;
			ns_lookup(name);

			if (ReceivePortNameServerProtocol.DEBUG) System.err.println(Thread.currentThread() + "ReceivePortNSClient: Wait for my lookup \"" + name + "\" reply " + ns_done);
			Ibis.myIbis.waitPolling(this, BACKOFF_MILLIS, Poll.PREEMPTIVE);
			if (ReceivePortNameServerProtocol.DEBUG) System.err.println(Thread.currentThread() + "ReceivePortNSClient: Lookup reply says ri = " + ri + " ns_done = " + ns_done);

			if (ri != null && ri.cpu != -1) {
			    if (ReceivePortNameServerProtocol.DEBUG) System.err.println(Thread.currentThread() + "ReceivePortNSClient: clear lookup.ns_busy" + this);
			    ns_busy = false;
			    if (ReceivePortNameServerProtocol.DEBUG) System.err.println(Thread.currentThread() + "ReceivePortNSClient: signal potential waiters");
			    ns_free.cv_signal();
			    return ri;
			}
		    } finally {
			if (ReceivePortNameServerProtocol.DEBUG) System.err.println("Releasing lock ...");
			Ibis.myIbis.unlock();
			if (ReceivePortNameServerProtocol.DEBUG) System.err.println("Released lock ...");
		    }
		    last_try = System.currentTimeMillis();
		}
		/* Thread.yield(); */

		if (false) {
		    // I don't see why I should sleep here, when waitPolling
		    // also takes a timeout argument
		    try {
			Thread.sleep(BACKOFF_MILLIS);
		    } catch (InterruptedException e) {
			// Well, if somebody interrupts us, would there be news?
		    }
		}
	    }
	}
    }

    native void ns_lookup(String name);

    /* Called from native */
    private void lookup_reply(byte[] rcvePortId) {
	Ibis.myIbis.checkLockOwned();
	if (ReceivePortNameServerProtocol.DEBUG) System.err.println(Thread.currentThread() + "ReceivePortNSClient: lookup reply " + rcvePortId + " " + lookup.ns_done);
	lookup.ri = null;
	if (rcvePortId != null) {
	    try {
		lookup.ri = (ReceivePortIdentifier)SerializeBuffer.readObject(rcvePortId);
		lookup.ns_done.cv_signal();
	    } catch (IbisIOException e) {
		System.err.println("Cannot deserialize ReceivePortId");
		Thread.dumpStack();
	    }
	}
    }

    Lookup lookup = new Lookup();

    public ibis.ipl.ReceivePortIdentifier lookup(String name, long timeout) throws IbisIOException {
	if (ReceivePortNameServerProtocol.DEBUG) {
	    System.err.println(Ibis.myIbis.myCpu + ": Do a ReceivePortId NS lookup(" + name + ", " + timeout + ") in " + lookup);
	}
	return lookup.lookup(name, timeout);
    }

    public ibis.ipl.ReceivePortIdentifier[] query(ibis.ipl.IbisIdentifier ident) throws IbisIOException {
	/* not implemented yet */
	return null;
    }

    native void ns_unbind(String public_name);

    void unbind(String name) {
	Ibis.myIbis.lock();
	if (ReceivePortNameServerProtocol.DEBUG) {
	    System.err.println(Ibis.myIbis.myCpu + ": Do an UNBIND of \"" + name + "\"");
	}
	ns_unbind(name);
	Ibis.myIbis.unlock();
    }
}
