package ibis.ipl.impl.net;

import ibis.ipl.IbisException;
import ibis.ipl.ReadMessage;
import ibis.ipl.SendPortIdentifier;

import java.util.Hashtable;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.util.Hashtable;

/**
 * Provide an abstraction of a network input.
 */
public abstract class NetInput extends NetIO implements ReadMessage, NetInputUpcall {
	/**
	 * Active {@link NetConnection connection} number or <code>null</code> if
	 * no connection is active.
	 */
	private		volatile 	Integer                 activeNum              = null;
        private   	volatile 	PooledUpcallThread      activeThread           = null;
        private   	final    	int                     threadStackSize        =  256;
        private  			int                     threadStackPtr         =    0;
        private  			PooledUpcallThread[] 	threadStack            = new PooledUpcallThread[threadStackSize];
        private				int                  	upcallThreadNum        =    0;
        private                         volatile boolean        upcallThreadNotStarted = true;
        private                         NetThreadStat           utStat                 = null;

        /**
         * Upcall interface for incoming messages.
         */
        protected          NetInputUpcall upcallFunc = null;

        static private volatile int     threadCount = 0;
        static private          boolean globalThreadStat = false;
        static {
                if (globalThreadStat) {
                        Runtime.getRuntime().addShutdownHook(new Thread() {
                                        public void run() {
                                                System.err.println("used "+threadCount+" Upcall threads");
                                                System.err.println("current memory values: "+Runtime.getRuntime().totalMemory()+"/"+Runtime.getRuntime().freeMemory()+"/"+Runtime.getRuntime().maxMemory());
                                        }
                                });
                }

        }


        public final class NetThreadStat extends NetStat {
                private int nb_thread_requested = 0;
                private int nb_thread_allocated = 0;
                private int nb_thread_reused    = 0;
                private int nb_thread_discarded = 0;
                private int nb_max_thread_stack = 0;

                public NetThreadStat(boolean on, String moduleName) {
                        super(on, moduleName);

                        if (on) {
                                pluralExceptions.put("entry", "entries");
                        }
                }

                public NetThreadStat(boolean on) {
                        this(on, "");
                }

                public void addAllocation() {
                        if (on) {
                                nb_thread_allocated++;
                                nb_thread_requested++;
                        }

                }

                public void addReuse() {
                        if (on) {

                                nb_thread_requested++;
                                nb_thread_reused++;
                        }

                }

                public void addStore() {
                        if (on) {
                                if (nb_max_thread_stack < threadStackPtr) {
                                        nb_max_thread_stack = threadStackPtr;
                                }
                        }
                }

                public void addDiscarded() {
                        if (on) {
                                nb_thread_discarded++;
                        }

                }

                public void report() {
                        if (on) {
                                System.err.println();
                                System.err.println("Upcall thread allocation stats for module "+moduleName);
                                System.err.println("------------------------------------");

                                reportVal(nb_thread_requested, " thread request");
                                reportVal(nb_thread_allocated, " thread allocation");
                                reportVal(nb_thread_reused   , " thread reuse");
                                reportVal(nb_thread_discarded   , " thread discardal");
                                reportVal(nb_max_thread_stack, " stack", "entry", "used");
                        }
                }
        }



        private final class PooledUpcallThread extends Thread {
                private boolean  end   = false;
                private NetMutex sleep = new NetMutex(true);
                public PooledUpcallThread(String name) {
                        super("NetInput.PooledUpcallThread: "+name);
                        threadCount++;
                }

                public void run() {
                        log.in();
                        while (!end) {
                                log.disp("sleeping...");
                                try {
                                        sleep.ilock();
                                        activeThread = this;
                                } catch (InterruptedException e) {
                                        log.disp("was interrupted...");
                                        continue;
                                }

                                log.disp("just woke up, polling...");
                                while (!end) {
                                        try {
                                                activeNum = poll(true);
                                                if (activeNum == null) {
                                                        // the connection was probably closed
                                                        // let the 'while' test the end flag
                                                        continue;
                                                }
                                                initReceive(activeNum);
                                        } catch (NetIbisClosedException e) {
                                                end = true;
                                                return;
                                        } catch (NetIbisException e) {
                                                throw new Error(e);
                                        }

                                        try {
                                                upcallFunc.inputUpcall(NetInput.this, activeNum);
                                        } catch (NetIbisInterruptedException e) {
                                                if (end != true) {
                                                        throw new Error(e);
                                                }
                                                return;
                                        } catch (NetIbisClosedException e) {
                                                end = true;
                                                return;
                                        } catch (NetIbisException e) {
                                                throw new Error(e);
                                        }


                                        if (activeThread == this) {

                                                try {
                                                        implicitFinish();
                                                } catch (Exception e) {
                                                        throw new Error(e.getMessage());
                                                }
                                                log.disp("reusing thread");
                                                utStat.addReuse();
                                                continue;
                                        } else {
                                                synchronized (threadStack) {
                                                        if (threadStackPtr < threadStackSize) {
                                                                threadStack[threadStackPtr++] = this;
                                                                log.disp("storing thread into the stack");
                                                                utStat.addStore();
                                                        } else {
                                                                log.disp("discarding the thread");
                                                                return;
                                                        }
                                                }
                                                break;
                                        }
                                }
                        }
                        log.out();
                }

                public void exec() {
                        log.in();
                        sleep.unlock();
                        log.out();
                }

                public void end() {
                        log.in();
                        end = true;
                        this.interrupt();
                        log.out();
                }
        }



	/**
	 * Constructor.
	 *
	 * @param portType the port {@link NetPortType} type.
	 * @param driver the driver.
	 * @param context the context.
	 */
	protected NetInput(NetPortType portType,
			   NetDriver   driver,
                           String      context) {
		super(portType, driver, context);
		// setBufferFactory(new NetBufferFactory(new NetReceiveBufferFactoryDefaultImpl()));
                // Stat object
                String s = "//"+type.name()+this.context+".input";
                boolean utStatOn = type.getBooleanStringProperty(this.context, "UpcallThreadStat", false);
                //System.err.println("utStatOn = "+utStatOn);
                utStat = new NetThreadStat(utStatOn, s);
	}

        /**
         * Default incoming message upcall method.
         *
         * Note: this method is only useful for filtering drivers.
         *
         * @param input the {@link NetInput sub-input} that generated the upcall.
         * @param num   the active connection number
         * @exception NetIbisException in case of trouble.
         */
        public synchronized void inputUpcall(NetInput input, Integer num) throws NetIbisException {
                log.in();
                activeNum = num;
                upcallFunc.inputUpcall(this, num);
                activeNum = null;
                log.out();
        }

        protected abstract void initReceive(Integer num) throws NetIbisException;

	/**
	 * Test for incoming data.
	 *
	 * Note: if {@linkplain #poll} is called again immediately
	 * after a successful {@linkplain #poll} without extracting the message and
	 * {@linkplain #finish finishing} the input, the result is
	 * undefined and data might get lost. Use {@link
	 * #getActiveSendPortNum} instead.
	 *
	 * @param blockForMessage indicates whether this method must block until
	 *        a message has arrived, or just query the input one.
	 * @return the {@link NetConnection connection} identifier or <code>null</code> if no data is available.
	 * @exception NetIbisException if the polling fails (!= the
	 * polling is unsuccessful).
	 */
	public final Integer poll(boolean blockForMessage) throws NetIbisException {
                log.in();
                if (activeNum != null) {
                        throw new Error("invalid state");
                }

                activeNum = null;

                activeNum = doPoll(blockForMessage);

                if (activeNum != null) {
                        initReceive(activeNum);
                }
                log.out();

                return activeNum;
        }

	protected abstract Integer doPoll(boolean blockForMessage) throws NetIbisException;

	/**
	 * Unblockingly test for incoming data.
	 *
	 * Note: if {@linkplain #poll} is called again immediately
	 * after a successful {@linkplain #poll} without extracting the message and
	 * {@linkplain #finish finishing} the input, the result is
	 * undefined and data might get lost. Use {@link
	 * #getActiveSendPortNum} instead.
	 *
	 * @return the {@link NetConnection connection} identifier or <code>null</code> if no data is available.
	 * @exception NetIbisException if the polling fails (!= the
	 * polling is unsuccessful).
         */
	public Integer poll() throws NetIbisException {
	    return poll(false);
	}

	/**
	 * Return the active {@link NetConnection connection} identifier or <code>null</code> if no {@link NetConnection connection} is active.
	 *
	 * @return the active {@link NetConnection connection} identifier or <code>null</code> if no {@link NetConnection connection} is active.
	 */
	public final Integer getActiveSendPortNum() {
		return activeNum;
	}

        /**
	 * Actually establish a connection with a remote port and register an upcall function for incoming message notification.
	 *
	 * @param cnx the connection attributes.
         * @param inputUpcall the upcall function for incoming message notification.
	 * @exception NetIbisException if the connection setup fails.
	 */
	public synchronized void setupConnection(NetConnection  cnx,
                                                 NetInputUpcall inputUpcall) throws NetIbisException {
                log.in();
                this.upcallFunc = inputUpcall;
                log.disp("this.upcallFunc = "+this.upcallFunc);
                setupConnection(cnx);
                log.out();
        }

        protected final void startUpcallThread() {
                log.in();
                synchronized(threadStack) {
                        if (upcallFunc != null && upcallThreadNotStarted) {
                                upcallThreadNotStarted = false;
                                PooledUpcallThread up = new PooledUpcallThread("no "+upcallThreadNum++);
                                utStat.addAllocation();
                                up.start();
                                up.exec();
                        }
                }
                log.out();
        }


	/**
	 * Utility function to get a {@link NetReceiveBuffer} from our
	 * {@link NetBufferFactory}.
	 * This is only valid for a Factory with MTU.
	 *
         * @param contentsLength indicates how many bytes of data must be received.
         * 0 indicates that any length is fine and that the buffer.length field
         * should be filled with the length actually read.
	 * @throws an {@link NetIbisException} if the factory has no default MTU
         * @return the new {@link NetReceiveBuffer}.
	 */
	public NetReceiveBuffer createReceiveBuffer(int contentsLength) throws NetIbisException {
                log.in();
                NetReceiveBuffer b = (NetReceiveBuffer)createBuffer();
                b.length = contentsLength;
                log.out();
                return b;
	}

	/**
	 * Utility function to get a {@link NetReceiveBuffer} from our
	 * {@link NetBufferFactory}.
	 *
	 * @param length the length of the data stored in the buffer
         * @param contentsLength indicates how many bytes of data must be received.
         * 0 indicates that any length is fine and that the buffer.length field
         * should be filled with the length actually read.
         * @return the new {@link NetReceiveBuffer}.
	 */
	public NetReceiveBuffer createReceiveBuffer(int length, int contentsLength)
		throws NetIbisException {
                log.in();
                NetReceiveBuffer b = (NetReceiveBuffer)createBuffer(length);
                b.length = contentsLength;
                log.out();
                return b;
	}

        public final synchronized void close(Integer num) throws NetIbisException {
                log.in();
                doClose(num);
                if (activeNum == num) {
                        activeNum = null;
                }

                synchronized (threadStack) {
                        if (activeThread != null) {
                                ((PooledUpcallThread)activeThread).end();
                        }

                        while (threadStackPtr > 0) {
                                threadStack[--threadStackPtr].end();
                        }
                        upcallThreadNotStarted = true;
                }
                log.out();
        }

        protected abstract void doClose(Integer num) throws NetIbisException;

	/*
         * Closes the I/O.
	 *
	 * Note: methods redefining this one should also call it, just in case
         *       we need to add something here
         * @exception NetIbisException if this operation fails.
	 */
	public void free() throws NetIbisException {
                log.in();
                doFree();
		activeNum = null;
                synchronized (threadStack) {
                        if (activeThread != null) {
                                ((PooledUpcallThread)activeThread).end();
                                while (true) {
                                        try {
                                                ((PooledUpcallThread)activeThread).join();
                                                break;
                                        } catch (InterruptedException e) {
                                                //
                                        }
                                }
                        }

                        for (int i = 0; i < threadStackSize; i++) {
                                if (threadStack[i] != null) {
                                        threadStack[i].end();
                                        while (true) {
                                                try {
                                                        threadStack[i].join();
                                                        break;
                                                } catch (InterruptedException e) {
                                                        //
                                                }
                                        }
                                }
                        }
                }
		super.free();
                log.out();
	}

	protected abstract void doFree() throws NetIbisException;

	/**
         * {@inheritDoc}
	 */
	protected void finalize() throws Throwable {
                log.in();
		free();
		super.finalize();
                log.out();
	}


        /* ReadMessage Interface */

        private final void implicitFinish() throws NetIbisException {
                log.in();
                if (_inputConvertStream != null) {
                        try {
                                _inputConvertStream.close();
                        } catch (IOException e) {
                                throw new NetIbisException(e.getMessage());
                        }

                        _inputConvertStream = null;
                }

                doFinish();

                activeNum = null;
                log.out();
        }

	/**
         * Complete the current incoming message extraction.
         *
         * Only one message is alive at one time for a given
         * receiveport. This is done to prevent flow control
         * problems. when a message is alive, and a new messages is
         * requested with a receive, the requester is blocked until
         * the live message is finished.
         *
         * @exception NetIbisException in case of trouble.
         */
       	public final void finish() throws NetIbisException {
                log.in();
                implicitFinish();
                if (activeThread != null) {
                        PooledUpcallThread ut = null;

                        synchronized(threadStack) {
                                if (threadStackPtr > 0) {
                                        ut = threadStack[--threadStackPtr];
                                        utStat.addReuse();
                                } else {
                                        ut = new PooledUpcallThread("no "+upcallThreadNum++);
                                        ut.start();
                                        utStat.addAllocation();
                                }
                        }

                        activeThread = ut;

                        if (ut != null) {
                                ut.exec();
                        }
                }
                log.out();
        }

        protected abstract void doFinish() throws NetIbisException;

        /**
         * Unimplemented.
         *
         * @return 0.
         */
	public long sequenceNumber() {
                return 0;
        }


        /**
         * Unimplemented.
         *
         * @return <code>null</code>.
         */
	public SendPortIdentifier origin() {
                return null;
        }




        /* fallback serialization implementation */

        /**
         * Object stream for the internal fallback serialization.
         *
         * Note: the fallback serialization implementation internally uses
         * a JVM {@link ObjectOutputStream}/{@link ObjectInputStream} pair.
         * The stream pair is closed upon each message completion to ensure data
         * consistency.
	 */
        private ObjectInputStream        _inputConvertStream = null;



        /**
         * Check whether the convert stream should be initialized, and
         * initialize it when needed.
	 */
        private final void checkConvertStream() throws IOException {
                if (_inputConvertStream == null) {
                        DummyInputStream dis = new DummyInputStream();
                        _inputConvertStream = new ObjectInputStream(dis);
                }
        }

        /**
         * Default implementation of {@link #readBoolean}.
         *
         * Note: this method must not be changed.
         *
         * @return the <code>boolean</code> value just read.
         * @exception NetIbisException in case of trouble.
         */
        private final boolean defaultReadBoolean() throws NetIbisException {
                boolean result = false;

                try {
                        checkConvertStream();
                        result = _inputConvertStream.readBoolean();
                } catch (IOException e) {
                        throw new NetIbisException(e.getMessage());
                }

                return result;
        }

        /**
         * Default implementation of {@link #readChar}.
         *
         * Note: this method must not be changed.
         *
         * @return the <code>char</code> value just read.
         * @exception NetIbisException in case of trouble.
         */
        private final char defaultReadChar() throws NetIbisException {
                char result = 0;

                try {
                        checkConvertStream();
                        result = _inputConvertStream.readChar();
                } catch (IOException e) {
                        throw new NetIbisException(e.getMessage());
                }

                return result;
        }

        /**
         * Default implementation of {@link #readShort}.
         *
         * Note: this method must not be changed.
         *
         * @return the <code>short</code> value just read.
         * @exception NetIbisException in case of trouble.
         */
        private final short defaultReadShort() throws NetIbisException {
                short result = 0;

                try {
                        checkConvertStream();
                        result = _inputConvertStream.readShort();
                } catch (IOException e) {
                        throw new NetIbisException(e.getMessage());
                }

                return result;
        }

        /**
         * Default implementation of {@link #readInt}.
         *
         * Note: this method must not be changed.
         *
         * @return the <code>int</code> value just read.
         * @exception NetIbisException in case of trouble.
         */
        private final int defaultReadInt() throws NetIbisException {
                int result = 0;

                try {
                        checkConvertStream();
                        result = _inputConvertStream.readInt();
                } catch (IOException e) {
System.err.println("Catch exception " + e);
                        throw new NetIbisException(e.getMessage());
                }

                return result;
        }

        /**
         * Default implementation of {@link #readLong}.
         *
         * Note: this method must not be changed.
         *
         * @return the <code>long</code> value just read.
         * @exception NetIbisException in case of trouble.
         */
        private final long defaultReadLong() throws NetIbisException {
                long result = 0;

                try {
                        checkConvertStream();
                        result = _inputConvertStream.readLong();
                } catch (IOException e) {
                        throw new NetIbisException(e.getMessage());
                }

                return result;
        }

        /**
         * Default implementation of {@link #readFloat}.
         *
         * Note: this method must not be changed.
         *
         * @return the <code>float</code> value just read.
         * @exception NetIbisException in case of trouble.
         */
        private final float defaultReadFloat() throws NetIbisException {
                float result = 0;

                try {
                        checkConvertStream();
                        result = _inputConvertStream.readFloat();
                } catch (IOException e) {
                        throw new NetIbisException(e.getMessage());
                }

                return result;
        }

        /**
         * Default implementation of {@link #readDouble}.
         *
         * Note: this method must not be changed.
         *
         * @return the <code>double</code> value just read.
         * @exception NetIbisException in case of trouble.
         */
        private final double defaultReadDouble() throws NetIbisException {
                double result = 0;

                try {
                        checkConvertStream();
                        result = _inputConvertStream.readDouble();
                } catch (IOException e) {
                        throw new NetIbisException(e.getMessage());
                }

                return result;
        }

        /**
         * Default implementation of {@link #readString}.
         *
         * Note: this method must not be changed.
         *
         * @return the {@link String string} value just read.
         * @exception NetIbisException in case of trouble.
         */
        private final String defaultReadString() throws NetIbisException {
                String result = null;

                try {
                        checkConvertStream();
                        result = _inputConvertStream.readUTF();
                } catch (Exception e) {
                        throw new NetIbisException(e.getMessage());
                }

                return result;
        }

        /**
         * Default implementation of {@link #readObject}.
         *
         * Note: this method must not be changed.
         *
         * @return the {@link Object object} value just read.
         * @exception NetIbisException in case of trouble.
         */
        private final Object defaultReadObject() throws NetIbisException {
                Object result = null;

                try {
                        checkConvertStream();
                        result = _inputConvertStream.readObject();
                } catch (Exception e) {
                        throw new NetIbisException(e.getMessage());
                }

                return result;
        }

        /**
         * Default implementation of {@link #readArray}.
         *
         * Note: this method must not be changed.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to read.
         * @exception NetIbisException in case of trouble.
         */
        private final void defaultReadArray(boolean [] b, int o, int l) throws NetIbisException {
                try {
                        checkConvertStream();
                        while (l-- > 0) {
                                b[o++] = _inputConvertStream.readBoolean();
                        }
                } catch (IOException e) {
                        throw new NetIbisException(e.getMessage());
                }
        }

        /**
         * Default implementation of {@link #readBoolean}.
         *
         * Note: this method must not be changed.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to read.
         * @exception NetIbisException in case of trouble.
         */
        private final void defaultReadArray(byte [] b, int o, int l) throws NetIbisException {
                try {
                        checkConvertStream();
                        while (l-- > 0) {
                                b[o++] = _inputConvertStream.readByte();
                        }
                } catch (IOException e) {
                        throw new NetIbisException(e);
                }
        }

        /**
         * Default implementation of {@link #readBoolean}.
         *
         * Note: this method must not be changed.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to read.
         * @exception NetIbisException in case of trouble.
         */
        private final void defaultReadArray(char [] b, int o, int l) throws NetIbisException {
                try {
                        checkConvertStream();
                        while (l-- > 0) {
                                b[o++] = _inputConvertStream.readChar();
                        }
                } catch (IOException e) {
                        throw new NetIbisException(e.getMessage());
                }
        }

        /**
         * Default implementation of {@link #readBoolean}.
         *
         * Note: this method must not be changed.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to read.
         * @exception NetIbisException in case of trouble.
         */
        private final void defaultReadArray(short [] b, int o, int l) throws NetIbisException {
                try {
                        checkConvertStream();
                        while (l-- > 0) {
                                b[o++] = _inputConvertStream.readShort();
                        }
                } catch (IOException e) {
                        throw new NetIbisException(e.getMessage());
                }
        }

        /**
         * Default implementation of {@link #readBoolean}.
         *
         * Note: this method must not be changed.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to read.
         * @exception NetIbisException in case of trouble.
         */
        private final void defaultReadArray(int [] b, int o, int l) throws NetIbisException {
                try {
                        checkConvertStream();
                        while (l-- > 0) {
                                b[o++] = _inputConvertStream.readInt();
                        }
                } catch (IOException e) {
                        throw new NetIbisException(e.getMessage());
                }
        }

        /**
         * Default implementation of {@link #readBoolean}.
         *
         * Note: this method must not be changed.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to read.
         * @exception NetIbisException in case of trouble.
         */
        private final void defaultReadArray(long [] b, int o, int l) throws NetIbisException {
                try {
                        checkConvertStream();
                        while (l-- > 0) {
                                b[o++] = _inputConvertStream.readLong();
                        }
                } catch (IOException e) {
                        throw new NetIbisException(e.getMessage());
                }
        }

        /**
         * Default implementation of {@link #readBoolean}.
         *
         * Note: this method must not be changed.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to read.
         * @exception NetIbisException in case of trouble.
         */
        private final void defaultReadArray(float [] b, int o, int l) throws NetIbisException {
                try {
                        checkConvertStream();
                        while (l-- > 0) {
                                b[o++] = _inputConvertStream.readFloat();
                        }
                } catch (IOException e) {
                        throw new NetIbisException(e.getMessage());
                }
        }

        /**
         * Default implementation of {@link #readBoolean}.
         *
         * Note: this method must not be changed.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to read.
         * @exception NetIbisException in case of trouble.
         */
        private final void defaultReadArray(double [] b, int o, int l) throws NetIbisException {
                try {
                        checkConvertStream();
                        while (l-- > 0) {
                                b[o++] = _inputConvertStream.readDouble();
                        }
                } catch (IOException e) {
                        throw new NetIbisException(e.getMessage());
                }
        }

        /**
         * Default implementation of {@link #readBoolean}.
         *
         * Note: this method must not be changed.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to read.
         * @exception NetIbisException in case of trouble.
         */
        private final void defaultReadArray(Object [] b, int o, int l) throws NetIbisException {
                try {
                        checkConvertStream();
                        while (l-- > 0) {
                                b[o++] = _inputConvertStream.readObject();
                        }
                } catch (Exception e) {
                        throw new NetIbisException(e.getMessage());
                }
        }

        /**
         * Atomic packet read function.
         *
         * @param expectedLength a hint about how many bytes are expected.
         * @exception NetIbisException in case of trouble.
         */
        public NetReceiveBuffer readByteBuffer(int expectedLength) throws NetIbisException {
                int len = defaultReadInt();
		NetReceiveBuffer buffer = createReceiveBuffer(len);
                defaultReadArray(buffer.data, 0, len);
                return buffer;
        }

        /**
         * Atomic packet read function.
         *
         * @param b the buffer to fill.
         * @exception NetIbisException in case of trouble.
         */
        public void readByteBuffer(NetReceiveBuffer buffer) throws NetIbisException {
                int len = defaultReadInt();
                defaultReadArray(buffer.data, 0, len);
                buffer.length = len;
        }

        /**
         * Extract an element from the current message.
         *
         * @exception NetIbisException in case of trouble.
         */
	public boolean readBoolean() throws NetIbisException {
                return defaultReadBoolean();
        }

        /**
         * Extract a byte from the current message.
         *
         * @exception NetIbisException in case of trouble.
         */
	public abstract byte readByte() throws NetIbisException;

        /**
         * Extract an element from the current message.
         *
         * @exception NetIbisException in case of trouble.
         */
	public char readChar() throws NetIbisException {
                return defaultReadChar();
        }

        /**
         * Extract an element from the current message.
         *
         * @exception NetIbisException in case of trouble.
         */
	public short readShort() throws NetIbisException {
                return defaultReadShort();
        }

        /**
         * Extract an element from the current message.
         *
         * @exception NetIbisException in case of trouble.
         */
	public int readInt() throws NetIbisException {
                return defaultReadInt();
        }

        /**
         * Extract an element from the current message.
         *
         * @exception NetIbisException in case of trouble.
         */
	public long readLong() throws NetIbisException {
                return defaultReadLong();
        }

        /**
         * Extract an element from the current message.
         *
         * @exception NetIbisException in case of trouble.
         */
	public float readFloat() throws NetIbisException {
                return defaultReadFloat();
        }

        /**
         * Extract an element from the current message.
         *
         * @exception NetIbisException in case of trouble.
         */
	public double readDouble() throws NetIbisException {
                return defaultReadDouble();
        }

        /**
         * Extract an element from the current message.
         *
         * @exception NetIbisException in case of trouble.
         */
	public String readString() throws NetIbisException {
                return (String)defaultReadString();
        }

        /**
         * Extract an element from the current message.
         *
         * @exception NetIbisException in case of trouble.
         */
	public Object readObject() throws NetIbisException {
                return defaultReadObject();
        }


        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to extract.
         *
         * @exception NetIbisException in case of trouble.
         */
	public void readArray(boolean [] b, int o, int l) throws NetIbisException {
                defaultReadArray(b, o, l);
        }

        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to extract.
         *
         * @exception NetIbisException in case of trouble.
         */
	public void readArray(byte [] b, int o, int l) throws NetIbisException {
                defaultReadArray(b, o, l);
        }

        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to extract.
         *
         * @exception NetIbisException in case of trouble.
         */
	public void readArray(char [] b, int o, int l) throws NetIbisException {
                defaultReadArray(b, o, l);
        }

        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to extract.
         *
         * @exception NetIbisException in case of trouble.
         */
	public void readArray(short [] b, int o, int l) throws NetIbisException {
                defaultReadArray(b, o, l);
        }

        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to extract.
         *
         * @exception NetIbisException in case of trouble.
         */
	public void readArray(int [] b, int o, int l) throws NetIbisException {
                defaultReadArray(b, o, l);
        }

        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to extract.
         *
         * @exception NetIbisException in case of trouble.
         */
	public void readArray(long [] b, int o, int l) throws NetIbisException {
                defaultReadArray(b, o, l);
        }

        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to extract.
         *
         * @exception NetIbisException in case of trouble.
         */
	public void readArray(float [] b, int o, int l) throws NetIbisException {
                defaultReadArray(b, o, l);
        }

        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to extract.
         *
         * @exception NetIbisException in case of trouble.
         */
	public void readArray(double [] b, int o, int l) throws NetIbisException {
                defaultReadArray(b, o, l);
        }

        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         * @param o the offset.
         * @param l the number of elements to extract.
         *
         * @exception NetIbisException in case of trouble.
         */
	public void readArray(Object [] b, int o, int l) throws NetIbisException {
                defaultReadArray(b, o, l);
        }


        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         *
         * @exception NetIbisException in case of trouble.
         */
	public final void readArray(boolean [] b) throws NetIbisException {
                readArray(b, 0, b.length);
        }

        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         *
         * @exception NetIbisException in case of trouble.
         */
	public final void readArray(byte [] b) throws NetIbisException {
                readArray(b, 0, b.length);
        }

        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         *
         * @exception NetIbisException in case of trouble.
         */
	public final void readArray(char [] b) throws NetIbisException {
                readArray(b, 0, b.length);
        }

        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         *
         * @exception NetIbisException in case of trouble.
         */
	public final void readArray(short [] b) throws NetIbisException {
                readArray(b, 0, b.length);
        }

        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         *
         * @exception NetIbisException in case of trouble.
         */
	public final void readArray(int [] b) throws NetIbisException {
                readArray(b, 0, b.length);
        }

        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         *
         * @exception NetIbisException in case of trouble.
         */
	public final void readArray(long [] b) throws NetIbisException {
                readArray(b, 0, b.length);
        }

        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         *
         * @exception NetIbisException in case of trouble.
         */
	public final void readArray(float [] b) throws NetIbisException {
                readArray(b, 0, b.length);
        }

        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         *
         * @exception NetIbisException in case of trouble.
         */
	public final void readArray(double [] b) throws NetIbisException {
                readArray(b, 0, b.length);
        }

        /**
         * Extract some elements from the current message.
         *
         * @param b the array.
         *
         * @exception NetIbisException in case of trouble.
         */
	public final void readArray(Object [] b) throws NetIbisException {
                readArray(b, 0, b.length);
        }


        /**
         * Internal dummy {@link InputStream} to be used as a byte stream source for
         * the {@link ObjectInputStream} based fallback serialization.
         */
        private final class DummyInputStream extends InputStream {

                /**
                 * {@inheritDoc}
                 *
                 * Note: the other read methods must _not_ be overloaded
                 *       because the ObjectInput/OutputStream do not guaranty
                 *       symmetrical transactions.
                 *
                 */
                public int read() throws IOException {
                        int result = 0;

                        try {
                                result = readByte();
                        } catch (NetIbisException e) {
                                throw new IOException(e.getMessage());
                        }

                        return (result & 255);
                }
        }
}
