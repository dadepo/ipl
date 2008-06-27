package ibis.ipl.impl.mx;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.log4j.Logger;

import ibis.io.DataOutputStream;

public class MxSimpleDataOutputStream extends DataOutputStream implements Config {
	//FIXME not threadsafe

	private static Logger logger = Logger.getLogger(MxSimpleDataOutputStream.class);
	
	private ByteBuffer buffer;
	private MxWriteChannel channel;
	private boolean sending = false;
	private boolean closed;
	private boolean closing = false;
	private long count;
	
	public MxSimpleDataOutputStream(MxWriteChannel channel) {
		//TODO multi channel support
		this.channel = channel;
		if (channel != null) {
			buffer = ByteBuffer.allocateDirect(BYTE_BUFFER_SIZE).order(channel.order);
		} else { //TODO come up with a reasonable solution
			// (in near future, this may be obsolete)
			buffer = ByteBuffer.allocateDirect(BYTE_BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN); 
		}
		count = 0;
		closed = false;
	}

	@Override
	public int bufferSize() {
		return buffer.capacity();
	}

	@Override
	public long bytesWritten() {
		return count;
	}

	@Override
	public void close() throws IOException {
		// Also closes the associated channel
		if (logger.isDebugEnabled()) {
			logger.debug("closing...");
		}
		closing = true;
		flush();
		if(!closed) {
			channel.close();
		}
		if(sending) {
			if(finished()) {
				closed = true;
			}
		} else {
			closed = true;
		}
		
		if (logger.isDebugEnabled()) {
			logger.debug("closed!");
		}
	}

	@Override
	public void flush() throws IOException {
    	if(closed) {
			throw new IOException("Stream is closed");
		}
		if(buffer.position() == 0) {
			// buffer is empty, don't send it
			return;
		}
		
		// post send for this buffer
		if(!sending) { //prevent sending the same buffer twice
			sending = true;
			buffer.flip();
			long size = buffer.remaining();
			if(size == 0) {
				//empty buffer, don't send, and we are ready
				buffer.clear();
				sending = false;
				return;
			}
			/*if (logger.isDebugEnabled()) {
				logger.debug("sending message...");
			}*/
			channel.write(buffer); 
			// TODO throws IOexceptions, catch them?
			count += size;
		}
	}
	
	@Override
	public void finish() throws IOException {
    	if(closed) {
			throw new IOException("Stream is closed");
		}
		if(sending) {
			channel.finish();
			buffer.clear();
			sending = false;
		}
		if(closing) {
			closed = true;
		}
	}

	@Override
	public boolean finished() throws IOException {
    	if(closed) {
			throw new IOException("Stream is closed");
		}
		if(!sending) {
			// we are not sending, so we are finished
			return true;
		}
		boolean finished = channel.poll();
		if(finished) {
			sending = false;
			buffer.clear();
			if(closing) {
				closed = true;
			}
		}
		return finished;
	}

	@Override
	public void resetBytesWritten() {
		count = 0;
	}

	@Override
	public void write(int arg0) throws IOException {
		writeByte((byte) arg0);
	}

	public void writeBoolean(boolean value) throws IOException {
        if (value) {
            writeByte((byte) 1);
        } else {
            writeByte((byte) 0);
        }
	}

	public void writeByte(byte value) throws IOException {
        /*if (logger.isDebugEnabled()) {
            logger.debug("writeByte(" + value + ")");
        }*/
        if(closing) {
        	throw new IOException("Stream is closed");
        }
        try {
            buffer.put(value);
        } catch (BufferOverflowException e) {
            // buffer was full, send
            flush();
            finish();
            // and try again
            buffer.put(value);
        }
	}

	public void writeChar(char value) throws IOException {
        /*if (logger.isDebugEnabled()) {
            logger.debug("writeChar(" + value + ")");
        }*/
        if(closing) {
        	throw new IOException("Stream is closed");
        }
        try {
            buffer.putChar(value);
        } catch (BufferOverflowException e) {
            // buffer was full, send
            flush();
            finish();
            // and try again
            buffer.putChar(value);
        }
	}

	public void writeDouble(double value) throws IOException {
        /*if (logger.isDebugEnabled()) {
            logger.debug("writeDouble(" + value + ")");
        }*/
        if(closing) {
        	throw new IOException("Stream is closed");
        }
        try {
            buffer.putDouble(value);
        } catch (BufferOverflowException e) {
            // buffer was full, send
            flush();
            finish();
            // and try again
            buffer.putDouble(value);
        }
	}

	public void writeFloat(float value) throws IOException {
        /*if (logger.isDebugEnabled()) {
            logger.debug("writeFloat(" + value + ")");
        }*/
        if(closing) {
        	throw new IOException("Stream is closed");
        }
        try {
            buffer.putFloat(value);
        } catch (BufferOverflowException e) {
            // buffer was full, send
            flush();
            finish();
            // and try again
            buffer.putFloat(value);
        }
	}

	public void writeInt(int value) throws IOException {
        /*if (logger.isDebugEnabled()) {
            logger.debug("writeInt(" + value + ")");
        }*/
        if(closing) {
        	throw new IOException("Stream is closed");
        }
        try {
            buffer.putInt(value);
        } catch (BufferOverflowException e) {
            // buffer was full, send
            flush();
            finish();
            // and try again
            buffer.putInt(value);
        }
	}

	public void writeLong(long value) throws IOException {
        if(closing) {
        	throw new IOException("Stream is closed");
        }
        /*if (logger.isDebugEnabled()) {
            logger.debug("writeLong(" + value + ")");
        }*/
        try {
            buffer.putLong(value);
        } catch (BufferOverflowException e) {
            // buffer was full, send
            flush();
            finish();
            // and try again
            buffer.putLong(value);
        }
	}

	public void writeShort(short value) throws IOException {
        if(closing) {
        	throw new IOException("Stream is closed");
        }
        /*if (logger.isDebugEnabled()) {
            logger.debug("writeShort(" + value + ")");
        }*/
        try {
            buffer.putShort(value);
        } catch (BufferOverflowException e) {
            // buffer was full, send
            flush();
            finish();
            // and try again
            buffer.putShort(value);
        }
	}
	
	public void writeArray(boolean[] source, int offset, int length)
			throws IOException {
        for (int i = offset; i < (offset + length); i++) {
            writeBoolean(source[i]);
        }
	}

	public void writeArray(byte[] source, int offset, int length)
			throws IOException {
        for (int i = offset; i < (offset + length); i++) {
            writeByte(source[i]);
        }
	}

	public void writeArray(char[] source, int offset, int length)
			throws IOException {
        for (int i = offset; i < (offset + length); i++) {
            writeChar(source[i]);
        }
	}

	public void writeArray(short[] source, int offset, int length)
			throws IOException {
        for (int i = offset; i < (offset + length); i++) {
            writeShort(source[i]);
        }
	}

	public void writeArray(int[] source, int offset, int length)
			throws IOException {
        for (int i = offset; i < (offset + length); i++) {
            writeInt(source[i]);
        }
	}

	public void writeArray(long[] source, int offset, int length)
			throws IOException {
        for (int i = offset; i < (offset + length); i++) {
            writeLong(source[i]);
        }
	}

	public void writeArray(float[] source, int offset, int length)
			throws IOException {
        for (int i = offset; i < (offset + length); i++) {
            writeFloat(source[i]);
        }
	}

	public void writeArray(double[] source, int offset, int length)
			throws IOException {
        for (int i = offset; i < (offset + length); i++) {
            writeDouble(source[i]);
        }
	}
}
