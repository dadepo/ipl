package ibis.ipl;

/** 
    For all write methods in this class, the invariant is that the reads must match the writes one by one.
    This means that the results are UNDEFINED when an array is written with writeXXXArray, but read with readObject.
**/

public interface WriteMessage { 

	/**
	 * Start sending the message to all ReceivePorts this SendPort is connected to.
	 * Data may be streamed, so the user is not allowed to touch the data, as the send is NON-blocking.
	 * @exception IbisIOException       an error occurred 
	 **/
	public void send() throws IbisIOException; 

	/**
	   Block until the entire message has been sent and clean up the message. Only after finish() or reset(), the data that was written
	   may be touched. Only one message is alive at one time for a given sendport. This is done to prevent flow control problems. 
	   When a message is alive and a new messages is requested, the requester is blocked until the
	   live message is finished. **/
	public void finish() throws IbisIOException;

	/**
	   If doSend, invoke send(). Then block until the entire message has been sent and clear data within the message. Only after finish() or reset(), the data that was written
	   may be touched. Only one message is alive at one time for a given sendport. This is done to prevent flow control problems. 
	   When a message is alive and a new messages is requested, the requester is blocked until the
	   live message is finished.
	   The message stays alive for subsequent writes and sends.
	   reset can be seen as a shorthand for (possibly send();) finish(); sendPort.newMessage() **/
	public void reset(boolean doSend) throws IbisIOException;

	/**
	   Return the number of bytes that was written to the message, in the stream dependant format.
	   This is the number of bytes that will be sent over the network **/
	public int getCount();

	/** Reset the counter */
	public void resetCount();

	/**
	 * Writes a boolean value to the message.
	 * @param     value             The boolean value to write.
	 */
	public void writeBoolean(boolean value) throws IbisIOException;

	/**
	 * Writes a byte value to the message.
	 * @param     value             The byte value to write.
	 */
	public void writeByte(byte value) throws IbisIOException;

	/**
	 * Writes a char value to the message.
	 * @param     value             The char value to write.
	 */
	public void writeChar(char value) throws IbisIOException;

	/**
	 * Writes a short value to the message.
	 * @param     value             The short value to write.
	 */
	public void writeShort(short value) throws IbisIOException;

	/**
	 * Writes a int value to the message.
	 * @param     value             The int value to write.
	 */
	public void writeInt(int value) throws IbisIOException;

	/**
	 * Writes a long value to the message.
	 * @param     value             The long value to write.
	 */
	public void writeLong(long value) throws IbisIOException;

	/**
	 * Writes a float value to the message.
	 * @param     value             The float value to write.
	 */
	public void writeFloat(float value) throws IbisIOException;

	/**
	 * Writes a double value to the message.
	 * @param     value             The double value to write.
	 */
	public void writeDouble(double value) throws IbisIOException;

	/**
	 * Writes a Serializable object to the message.
	 * @param     value             The object value to write.
	 */
	public void writeString(String value) throws IbisIOException;

	/**
	 * Writes a Serializable object to the message.
	 * Duplicate checks for the objects and arrays that are written are performed.
	 * @param     value             The object value to write.
	 */
	public void writeObject(Object value) throws IbisIOException;

	/** These methods can be used when the type of the array is known in advance.
	    Reading can be done in-place. WARNING: No cycle checks are done!
	    These methods are just a shortcut for doing:
	    writeArray(destination, 0, destination.length);

	    It is legal to use a writeArrayXXX, with a corresponding readArray.
	**/
	public void writeArray(boolean [] destination) throws IbisIOException;
	public void writeArray(byte [] destination) throws IbisIOException;
	public void writeArray(char [] destination) throws IbisIOException;
	public void writeArray(short [] destination) throws IbisIOException;
	public void writeArray(int [] destination) throws IbisIOException;
	public void writeArray(long [] destination) throws IbisIOException;
	public void writeArray(float [] destination) throws IbisIOException;
	public void writeArray(double [] destination) throws IbisIOException;
	public void writeArray(Object[] destination) throws IbisIOException;

	/** Write a clice of an array. No duplicate checks are done. 
	    It is legal to use a writeArray, with a corresponding readArrayXXX.
	**/
	public void writeArray(boolean [] destination, int offset, int size) throws IbisIOException;
	public void writeArray(byte [] destination, int offset, int size) throws IbisIOException;
	public void writeArray(char [] destination, int offset, int size) throws IbisIOException;
	public void writeArray(short [] destination, int offset, int size) throws IbisIOException;
	public void writeArray(int [] destination, int offset, int size) throws IbisIOException;
	public void writeArray(long [] destination, int offset, int size) throws IbisIOException;
	public void writeArray(float [] destination, int offset, int size) throws IbisIOException;
	public void writeArray(double [] destination, int offset, int size) throws IbisIOException;
	public void writeArray(Object [] destination, int offset, int size) throws IbisIOException;
} 
