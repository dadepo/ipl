package ibis.ipl.impl.messagePassing;

import ibis.ipl.impl.generic.ConditionVariable;

final class AcceptThread extends Thread {

    ReceivePort port;
    ibis.ipl.ReceivePortConnectUpcall upcall;
    ConditionVariable there_is_work = Ibis.myIbis.createCV();
    boolean	stopped;


    class AcceptQ {
	AcceptQ		next;
	boolean		finished;
	boolean		accept;
	ibis.ipl.SendPortIdentifier port;
	ConditionVariable decided = Ibis.myIbis.createCV();
    }

    AcceptQ	acceptQ_front;
    AcceptQ	acceptQ_tail;
    AcceptQ	acceptQ_freelist;


    AcceptThread(ReceivePort port, ibis.ipl.ReceivePortConnectUpcall upcall) {
	this.port = port;
	this.upcall = upcall;
    }


    private void enqueue(AcceptQ q) {
	q.next = null;
	if (acceptQ_front == null) {
	    acceptQ_front = q;
	} else {
	    acceptQ_tail.next = q;
	}
	acceptQ_tail = q;
    }


    private AcceptQ dequeue() {
	if (acceptQ_front == null) {
	    return null;
	}

	AcceptQ q = acceptQ_front;
	acceptQ_front = q.next;

	return q;
    }


    private AcceptQ get() {
	if (acceptQ_freelist == null) {
	    return new AcceptQ();
	}

	AcceptQ q = acceptQ_freelist;
	acceptQ_freelist = q.next;

	return q;
    }


    private void release(AcceptQ q) {
	q.next = acceptQ_freelist;
	acceptQ_freelist = q;
    }


    boolean checkAccept(ibis.ipl.SendPortIdentifier p) {
	boolean	accept;

	Ibis.myIbis.lock();

	AcceptQ q = get();

	q.port = p;
	enqueue(q);

	while (! q.finished) {
	    try {
		q.decided.cv_wait();
	    } catch (InterruptedException e) {
		// ignore
	    }
	}

	accept = q.accept;

	release(q);

	Ibis.myIbis.unlock();

	return accept;
    }


    public void run() {

	Ibis.myIbis.lock();

	AcceptQ q;

	while (true) {
	    while ((q = dequeue()) == null && ! stopped) {
		try {
		    there_is_work.cv_wait();
		} catch (InterruptedException e) {
		    // ignore
		}
	    }

	    if (q == null) {
		break;
	    }

	    q.accept = upcall.gotConnection(q.port);
	    q.finished = true;
	    q.decided.cv_signal();
	}

	Ibis.myIbis.unlock();
    }


    void free() {
	stopped = true;
	there_is_work.cv_signal();
    }

}
