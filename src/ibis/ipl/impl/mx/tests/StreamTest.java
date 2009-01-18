package ibis.ipl.impl.mx.tests;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;

import ibis.ipl.impl.mx.channels.*;

public class StreamTest implements MxListener {
	static int len         = 8 * 1024; // 4 or more
	static int count       = 10000;
	static int retries     = 10;
	
	MxSocket socket;
	ChannelManager manager;
	private boolean server;
	ReadChannel rc = null;
	InputStream is = null;
	OutputStream os = null;
	MxAddress sender;
	
	StreamTest() throws IOException {
		socket = new MxSocket(this);
		manager = socket.createChannelManager();
	}
	
	public void newConnection(ConnectionRequest request) {
		// TODO read implementation data
		CharBuffer cbuf = ByteBuffer.wrap(request.getDescriptor()).order(ByteOrder.BIG_ENDIAN).asCharBuffer();
		System.out.println("New request arrived: " + cbuf.toString());
		
		if(rc != null) {
			request.reject();
		} else {
			String str = "Hi There!";
			byte[] msg = new byte[2*str.length()];
			ByteBuffer buf = ByteBuffer.wrap(msg).order(ByteOrder.BIG_ENDIAN);
			buf.asCharBuffer().put(str);
			request.setReplyMessage(msg);
			rc = manager.accept(request);
			if(rc != null) {
				sender = request.getSourceAddress();
				synchronized(this) {
					notifyAll();
				}
			}
		}
	}
	
	void stream(boolean server) throws IOException {
			//long temp = 0, temp2 = 0;
			//long writeTime, flushTime, readTime;
			byte[] data = new byte[len];
			if (server) {
				System.out.println("Stream " + count + " * " + len + " bytes");
			}
			for(int j = 0; j < retries; j++) {
				if (server) {
					is.read();
					long time = System.currentTimeMillis();
					for(int i = 0; i < count; i++) {
						os.write(data);
						os.flush();
					}
					
					is.read();
					time = System.currentTimeMillis() - time;
					System.out.println("" + (double)time / 1000);
				} else {
					os.write(1);
					os.flush();
					for(int i = 0; i < count; i++) {
						int bytes = 0;
						while (bytes < len) {
							bytes += is.read(data, bytes, len);
						}
					}
					os.write(0);
					os.flush();
				}
			}
		}

	void pingPong(boolean server) throws IOException {
//		long temp = 0, temp2 = 0;
//		long writeTime, flushTime, readTime;
		byte[] data = new byte[len];
		if (server) {
			System.out.println("PingPong " + count + " * " + len + " bytes");
		}
		for(int j = 0; j < retries; j++) {
			if (server) {
//				writeTime = flushTime = readTime = 0;
				long time = System.currentTimeMillis();
				for(int i = 0; i < count; i++) {
//					temp = System.nanoTime();
					//os.write(i);
					os.write(data);
//					temp2 = System.nanoTime();
//					writeTime += temp2 - temp;
					os.flush();
//					temp = System.nanoTime();
//					flushTime += temp - temp2;
					/*if((byte)(is.read()) != (byte)i) {
						System.out.println("received wrong data, aborting");
						return;
					}*/
					int bytes = 0;
					while (bytes < len) {
						bytes += is.read(data, bytes, len);
					}
//					temp2 = System.nanoTime();
//					readTime += temp2 -temp;
				}
				time = System.currentTimeMillis() - time;
				//System.out.println("" + LOOPS + " PingPongs took " + time + " ms");
				System.out.println("" + (double)time / 1000);
				//System.out.println("Roundtrip time: " + (double)time/(double)count + " ms");
				//System.out.println("Bandwidth: " + (((double)count * (double)len * 2) / (double)(1000*1000))/ ((double) time / 1000) + " MB/s");
//				System.out.println("writeTime: " + writeTime + " ns");
//				System.out.println("flushTime: " + flushTime + " ns");
//				System.out.println("readTime: " + readTime + " ns");
			} else {
				for(int i = 0; i < count; i++) {
					/*if((byte)(is.read()) != (byte)i) {
						System.out.println("received wrong data, aborting");
						return;
					}*/
					int bytes = 0;
					while (bytes < len) {
						bytes += is.read(data, bytes, len);
					}
					//os.write(i);
					os.write(data);
					os.flush();
				}
			}
		}
	}
	
	void pingPing(boolean server) throws IOException {
		byte[] data = new byte[len];
		if (server) {
			System.out.println("PingPing " + count + " * " + len + " bytes");
		}
		
		for(int j = 0; j < retries; j++) {
			long time = System.currentTimeMillis();
			for(int i = 0; i < count; i++) {
				//os.write(i);
				os.write(data);
				os.flush();
				/*if((byte)(is.read()) != (byte)i) {
					System.out.println("received wrong data, aborting");
					return;
				}*/
				int bytes = 0;
				while (bytes < len) {
					bytes += is.read(data, bytes, len);
				}
				os.flush();
			}
			time = System.currentTimeMillis() - time;
			if(server) {
				//System.out.println("" + LOOPS + " PingPings took " + time + " ms");				
				System.out.println("latency: " + (double)time/(double)count + " ms");
				System.out.println("Bandwidth: " + (((double)count * (double)len) / (double)(1000*1000))/ ((double) time / 1000) + " MiB/s");
			}
		}
	}

	
	
	private void client() throws IOException {
		WriteChannel wc = null;
		String hostnames;
        String servername;
        
//      System.err.println("Client");                           
        try { // give the server some time to set up the link 
                Thread.sleep(1000);
        } catch (InterruptedException e) {
                //nothing
        }
        /* find the server address */
        /* on the DAS3 of the form nodeXXX:0 */
        hostnames = System.getenv("PRUN_HOSTNAMES");
        if (hostnames == null) {
                System.err.println("no hostenv");
                System.exit(1);
        }
        servername = hostnames.substring(0, 7) + ":0";
        
		MxAddress target = new MxAddress(servername, 0);
		
		try {
			String str = "Hello server!";
			byte[] ds = new byte[2*str.length()];
			ByteBuffer buf = ByteBuffer.wrap(ds).order(ByteOrder.BIG_ENDIAN);
			buf.asCharBuffer().put(str);
			
			Connection conn = manager.connect(target, ds);
			CharBuffer cbuf = ByteBuffer.wrap(conn.getReplyMessage()).order(ByteOrder.BIG_ENDIAN).asCharBuffer();
			System.out.println("Reply arrived: " + cbuf.toString());
			wc = conn.getWriteChannel();
		} catch (MxException e) {
			e.printStackTrace();
			System.exit(2);
		}
		
		while(rc == null) {
			synchronized(this) {
				try {
					wait();
				} catch (InterruptedException e) {
					// do nothing
				}
			}
		}
		is = rc.getInputStream();
		os = wc.getOutputStream();
		System.out.println("Client connected");
		
		pingPong(false);
		stream(false);
		//pingPing(false);
		os.close();
		is.close();
	}

	private void server() throws IOException {
		WriteChannel wc = null;
		while(rc == null) {
			synchronized(this) {
				try {
					wait();
				} catch (InterruptedException e) {
					// do nothing
				}
			}
		}
		
		try {
			String str = "Hello client!";
			byte[] ds = new byte[2*str.length()];
			ByteBuffer buf = ByteBuffer.wrap(ds).order(ByteOrder.BIG_ENDIAN);
			buf.asCharBuffer().put(str);
			Connection conn = manager.connect(sender, ds);
			CharBuffer cbuf = ByteBuffer.wrap(conn.getReplyMessage()).order(ByteOrder.BIG_ENDIAN).asCharBuffer();
			System.out.println("Reply arrived: " + cbuf.toString());
			wc = conn.getWriteChannel();
		} catch (MxException e) {
			e.printStackTrace();
			System.exit(2);
		}
		
		is = rc.getInputStream();
		os = wc.getOutputStream();
		System.out.println("Server connected");

		pingPong(true);
		stream(true);
		//pingPing(true);
		System.out.println();

		os.close();
		is.close();
	}

	private void run() throws IOException {
		String rank = System.getenv("PRUN_CPU_RANK");
        if(rank == null) {
                System.err.println("no rank env");
                return;
        }
        if(rank.equals("0")) {
                server = true;
        }
		if(server) {
			server();
			System.out.println("Server finished");
		} else {
			client();
			System.out.println("Client finished");
		}	
	}
	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		int i           = 0;

	    while (i < args.length) { 
			if (false) {
			} else if (args[i].equals("-len")) { 
			    len = Integer.parseInt(args[i+1]);
			    i += 2;
			} else if (args[i].equals("-count")) { 
			    count = Integer.parseInt(args[i+1]);
			    i += 2;
			} else if (args[i].equals("-retries")) { 
			    retries = Integer.parseInt(args[i+1]);
			    i += 2;
			} else {
			    System.err.println("unknown option: " + args[i]);
			    System.exit(1);
			}
	    }
		
		
		try {
			new StreamTest().run();
		} catch (MxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
