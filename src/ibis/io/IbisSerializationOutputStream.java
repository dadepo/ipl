package ibis.io;

import java.io.IOException;
import java.io.NotActiveException;
import java.io.NotSerializableException;
import java.io.ObjectOutput;
import java.io.ObjectStreamClass;


/**
 * This is the <code>SerializationOutputStream</code> version that is used for Ibis serialization.
 * An effort has been made to make it look like and extend <code>java.io.ObjectOutputStream</code>.
 * However, versioning is not supported, like it is in Sun serialization.
 */
public final class IbisSerializationOutputStream extends SerializationOutputStream implements IbisStreamFlags {
    /**
     * The underlying <code>IbisAccumulator</code>.
     * Must be public so that IOGenerator-generated code can access it.
     */
    public final IbisAccumulator out;

    /**
     * The first free object handle.
     */
    private int next_handle;

    /**
     * Hash table for keeping references to objects already written.
     */
    private IbisHash references  = new IbisHash(2048);

    /**
     * Remember when a reset must be sent out.
     */
    private boolean resetPending = false;

    /**
     * The first free type index.
     */
    private int next_type;

    /**
     * Hashtable for types already put on the stream.
     */
    private IbisHash types = new IbisHash();

    /**
     * There is a notion of a "current" object. This is needed when a user-defined
     * <code>writeObject</code> refers to <code>defaultWriteObject</code> or to
     * <code>putFields</code>.
     */
    private Object current_object;

    /**
     * There also is a notion of a "current" level.
     * The "level" of a serializable class is computed as follows:<ul>
     * <li> if its superclass is serializable: the level of the superclass + 1.
     * <li> if its superclass is not serializable: 1.
     * </ul>
     * This level implies a level at which an object can be seen. The "current"
     * level is the level at which <code>current_object</code> is being processed.
     */
    private int current_level;

    /**
     * There also is the notion of a "current" <code>PutField</code>, needed for
     * the <code>writeFields</code> method.
     */
    private ImplPutField current_putfield;

    /**
     * The <code>current_object</code>, <code>current_level</code>, and <code>current_putfield</code>
     * are maintained in stacks, so that they can be managed by IOGenerator-generated
     * code.
     */
    private Object[] object_stack;
    private int[] level_stack;
    private ImplPutField[] putfield_stack;
    private int max_stack_size = 0;
    private int stack_size = 0;

    /**
     * Storage for bytes (or booleans) written.
     */
    public byte[]	byte_buffer    = new byte[BYTE_BUFFER_SIZE];

    /**
     * Storage for chars written.
     */
    public char[]	char_buffer    = new char[CHAR_BUFFER_SIZE];

    /**
     * Storage for shorts written.
     */
    public short[]	short_buffer   = new short[SHORT_BUFFER_SIZE];

    /**
     * Storage for ints written.
     */
    public int[]	int_buffer     = new int[INT_BUFFER_SIZE];

    /**
     * Storage for longs written.
     */
    public long[]	long_buffer    = new long[LONG_BUFFER_SIZE];

    /**
     * Storage for floats written.
     */
    public float[]	float_buffer   = new float[FLOAT_BUFFER_SIZE];

    /**
     * Storage for doubles written.
     */
    public double[]	double_buffer  = new double[DOUBLE_BUFFER_SIZE];

    /**
     * Current index in <code>byte_buffer</code>.
     */
    public int		byte_index;

    /**
     * Current index in <code>char_buffer</code>.
     */
    public int		char_index;

    /**
     * Current index in <code>short_buffer</code>.
     */
    public int		short_index;

    /**
     * Current index in <code>int_buffer</code>.
     */
    public int		int_index;

    /**
     * Current index in <code>long_buffer</code>.
     */
    public int		long_index;

    /**
     * Current index in <code>float_buffer</code>.
     */
    public int		float_index;

    /**
     * Current index in <code>double_buffer</code>.
     */
    public int		double_index;

    /**
     * Structure summarizing an array write.
     */
    private static final class ArrayDescriptor {
	int	type;
	Object	array;
	int	offset;
	int	len;
    }

    /**
     * Where the arrays to be written are collected.
     */
    private ArrayDescriptor[] 	array = 
	new ArrayDescriptor[ARRAY_BUFFER_SIZE];

    /**
     * Index in the <code>array</code> array.
     */
    private int			array_index;

    /**
     * Collects all indices of the <code>..._buffer</code> arrays.
     */
    protected short[]	indices_short  = new short[PRIMITIVE_TYPES];

    /**
     * For each 
     */
    private boolean[] touched = new boolean[PRIMITIVE_TYPES];

    /**
     * Constructor with an <code>IbisAccumulator</code>.
     * @param out		the underlying <code>IbisAccumulator</code>
     * @exception IOException	gets thrown when an IO error occurs.
     */
    public IbisSerializationOutputStream(IbisAccumulator out)
							 throws IOException {
	super();

	for(int i = 0; i < ARRAY_BUFFER_SIZE; i++) {
	    array[i] = new ArrayDescriptor();
	}

	types_clear();

	next_type = PRIMITIVE_TYPES;
	this.out    = out;
	references.clear();
	next_handle = CONTROL_HANDLES;
    }

    /**
     * Debugging print.
     * @param s	the string to be printed.
     */
    private void dbPrint(String s) {
	IbisSerializationInputStream.debuggerPrint(this + ": " + s);
    }

    /**
     * Initializes the type hash by adding arrays of primitive types.
     */
    private void types_clear() {
	types.clear();
	types.put(classBooleanArray, TYPE_BOOLEAN | TYPE_BIT);
	types.put(classByteArray,    TYPE_BYTE | TYPE_BIT);
	types.put(classCharArray,    TYPE_CHAR | TYPE_BIT);
	types.put(classShortArray,   TYPE_SHORT | TYPE_BIT);
	types.put(classIntArray,     TYPE_INT | TYPE_BIT);
	types.put(classLongArray,    TYPE_LONG | TYPE_BIT);
	types.put(classFloatArray,   TYPE_FLOAT | TYPE_BIT);
	types.put(classDoubleArray,  TYPE_DOUBLE | TYPE_BIT);
	next_type = PRIMITIVE_TYPES;
    }

    /**
     * @inhetitDoc
     */
    public String serializationImplName() {
	return "ibis";
    }

    /**
     * @inheritDoc
     */
    public void reset() throws IOException {
	if (next_handle > CONTROL_HANDLES) {
	    if(DEBUG) {
		dbPrint("reset: next handle = " + next_handle + ".");
	    }
	    references.clear();
	    /* We cannot send out the reset immediately, because the
	     * reader side only accepts a reset when it is expecting a
	     * handle. So, instead, we remember that we need to send
	     * out a reset, and send before sending the next handle.
	     */
	    resetPending = true;
	    next_handle = CONTROL_HANDLES;
	}
	// types_clear();
	// There is no need to clear the type table.
	// It can be reused after a reset.
    }

    /**
     * @inheritDoc
     */
    public void statistics() {
	System.err.println("IbisOutput:");
	IbisHash.statistics();
    }

    /* This is the data output / object output part */

    /**
     * @inheritDoc
     */
    public void write(int v) throws IOException {
	writeByte((byte)(0xff & v));
    }

    /**
     * @inheritDoc
     */
    public void write(byte[] b) throws IOException {
	write(b, 0, b.length);
    }

    /**
     * @inheritDoc
     */
    public void write(byte[] b, int off, int len) throws IOException {
	writeArray(b, off, len);
    }

    /**
     * @inheritDoc
     */
    public void writeUTF(String str) throws IOException {
	// dbPrint("writeUTF: " + str);
	if(str == null) {
	    writeInt(-1);
	    return;
	}

	if(DEBUG) {
	    dbPrint("write UTF " + str);
	}
	int len = str.length();

	//	writeInt(len);
	//	writeArray(str.toCharArray(), 0, len);

	byte[] b = new byte[3 * len];
	int bn = 0;

	for (int i = 0; i < len; i++) {
	    char c = str.charAt(i);
	    if (c > 0x0000 && c <= 0x007f) {
		b[bn++] = (byte)c;
	    } else if (c <= 0x07ff) {
		b[bn++] = (byte)(0xc0 | (0x1f & (c >> 6)));
		b[bn++] = (byte)(0x80 | (0x3f & c));
	    } else {
		b[bn++] = (byte)(0xe0 | (0x0f & (c >> 12)));
		b[bn++] = (byte)(0x80 | (0x3f & (c >>  6)));
		b[bn++] = (byte)(0x80 | (0x3f & c));
	    }
	}

	writeInt(bn);
	writeArray(b, 0, bn);
    }

    /**
     * Called by IOGenerator-generated code to write a Class object to this stream.
     * For a Class object, only its name is written.
     * @param ref		the <code>Class</code> to be written
     * @exception IOException	gets thrown when an IO error occurs.
     */
    public void writeClass(Class ref) throws IOException {
	if (ref == null) {
	    writeHandle(NUL_HANDLE);
	    return;
	}
	int handle = references.find(ref);
	if (handle == 0) {
	    handle = next_handle++;
	    references.put(ref, handle);
	    writeType(java.lang.Class.class);
	    writeUTF(ref.getName());
	} else {
	    writeHandle(handle);
	}
    }

    /**
     * Initialize all buffer indices to zero.
     */
    final protected void reset_indices() {
	byte_index = 0;
	char_index = 0;
	short_index = 0;
	int_index = 0;
	long_index = 0;
	float_index = 0;
	double_index = 0;
    }

    /**
     * Method to put a array in the "array cache". If the cache is full
     * it is written to the arrayOutputStream
     * @param ref	the array to be written
     * @param offset	the offset at which to start
     * @param len	number of elements to write
     * @param type	type of the array elements
     *
     * @exception IOException on IO error.
     */
    private void writeArray(Object ref, int offset, int len, int type)
	    throws IOException {
	if (array_index == ARRAY_BUFFER_SIZE) {
	    flush();
	}
	if (DEBUG) {
	    dbPrint("writeArray: " + ref + " offset: " 
		    + offset + " len: " + len + " type: " + type);
	}
	array[array_index].type   = type;
	array[array_index].offset = offset;
	array[array_index].len 	  = len;
	array[array_index].array  = ref;
	array_index++;
    }

    /**
     * Flushes everything collected sofar.
     * @exception IOException on an IO error.
     */
    public void flush() throws IOException {

	if (DEBUG) {
	    dbPrint("doing a flush()");
	}
	flushBuffers();

	/* Retain the order in which the arrays were pushed. This 
	 * costs a cast at send/receive.
	 */
	for (int i = 0; i < array_index; i++) {
	    ArrayDescriptor a = array[i];
	    switch(a.type) {
	    case TYPE_BOOLEAN:
		out.writeArray( (boolean[])(a.array), a.offset, a.len);
		break;
	    case TYPE_BYTE:
		out.writeArray( (byte[])(a.array), a.offset, a.len);
		break;
	    case TYPE_CHAR:
		out.writeArray( (char[])(a.array), a.offset, a.len);
		break;
	    case TYPE_SHORT:
		out.writeArray( (short[])(a.array), a.offset, a.len);
		break;
	    case TYPE_INT:
		out.writeArray( (int[])(a.array), a.offset, a.len);
		break;
	    case TYPE_LONG:
		out.writeArray( (long[])(a.array), a.offset, a.len);
		break;
	    case TYPE_FLOAT:
		out.writeArray( (float[])(a.array), a.offset, a.len);
		break;
	    case TYPE_DOUBLE:
		out.writeArray( (double[])(a.array), a.offset, a.len);
		break;
	    }
	}

	array_index = 0;

	out.flush();

	if (out instanceof ArrayOutputStream) {

	    ArrayOutputStream o = (ArrayOutputStream) out;

	    if (! o.finished()) {
		indices_short = new short[PRIMITIVE_TYPES];
		if (touched[TYPE_BYTE]) {
		    byte_buffer   = new byte[BYTE_BUFFER_SIZE];
		}
		if (touched[TYPE_CHAR]) {
		    char_buffer   = new char[CHAR_BUFFER_SIZE];
		}
		if (touched[TYPE_SHORT]) {
		    short_buffer  = new short[SHORT_BUFFER_SIZE];
		}
		if (touched[TYPE_INT]) {
		    int_buffer    = new int[INT_BUFFER_SIZE];
		}
		if (touched[TYPE_LONG]) {
		    long_buffer   = new long[LONG_BUFFER_SIZE];
		}
		if (touched[TYPE_FLOAT]) {
		    float_buffer  = new float[FLOAT_BUFFER_SIZE];
		}
		if (touched[TYPE_DOUBLE]) {
		    double_buffer = new double[DOUBLE_BUFFER_SIZE];
		}
	    }
	}

	for (int i = 0; i < PRIMITIVE_TYPES; i++) {
	    touched[i] = false;
	}
    }

    /**
     * Flush the primitive arrays.
     *
     * @exception IOException is thrown when any <code>writeArray</code>
     * throws it.
     */
    public final void flushBuffers() throws IOException {
	indices_short[TYPE_BYTE]    = (short) byte_index;
	indices_short[TYPE_CHAR]    = (short) char_index;
	indices_short[TYPE_SHORT]   = (short) short_index;
	indices_short[TYPE_INT]     = (short) int_index;
	indices_short[TYPE_LONG]    = (short) long_index;
	indices_short[TYPE_FLOAT]   = (short) float_index;
	indices_short[TYPE_DOUBLE]  = (short) double_index;

	if (DEBUG) {
	    dbPrint("writing bytes " + byte_index);
	    dbPrint("writing chars " + char_index);
	    dbPrint("writing shorts " + short_index);
	    dbPrint("writing ints " + int_index);
	    dbPrint("writing longs " + long_index);
	    dbPrint("writing floats " + float_index);
	    dbPrint("writing doubles " + double_index);
	}

	out.writeArray(indices_short, BEGIN_TYPES, PRIMITIVE_TYPES-BEGIN_TYPES);

	if (byte_index > 0) {
	    out.writeArray(byte_buffer, 0, byte_index);
	    touched[TYPE_BYTE] = true;
	}
	if (char_index > 0) {
	    out.writeArray(char_buffer, 0, char_index);
	    touched[TYPE_CHAR] = true;
	}
	if (short_index > 0) {
	    out.writeArray(short_buffer, 0, short_index);
	    touched[TYPE_SHORT] = true;
	}
	if (int_index > 0) {
	    out.writeArray(int_buffer, 0, int_index);
	    touched[TYPE_INT] = true;
	}
	if (long_index > 0) {
	    out.writeArray(long_buffer, 0, long_index);
	    touched[TYPE_LONG] = true;
	}
	if (float_index > 0) {
	    out.writeArray(float_buffer, 0, float_index);
	    touched[TYPE_FLOAT] = true;
	}
	if (double_index > 0) {
	    out.writeArray(double_buffer, 0, double_index);
	    touched[TYPE_DOUBLE] = true;
	}

	reset_indices();
    }

    /**
     * Writes a boolean value to the accumulator.
     * @param     value             The boolean value to write.
     * @exception IOException on IO error.
     */
    public void writeBoolean(boolean value) throws IOException {
	if (byte_index == BYTE_BUFFER_SIZE) {
	    flush();
	}
	if (DEBUG) {
	    dbPrint("wrote boolean " + value);
	}
	byte_buffer[byte_index++] = (byte) (value ? 1 : 0);
    }


    /**
     * Writes a byte value to the accumulator.
     * @param     value             The byte value to write.
     * @exception IOException on IO error.
     */
    public void writeByte(int value) throws IOException {
	if (byte_index == BYTE_BUFFER_SIZE) {
	    flush();
	}
	if (DEBUG) {
	    dbPrint("wrote byte " + value);
	}
	byte_buffer[byte_index++] = (byte) value;
    }

    /**
     * Writes a char value to the accumulator.
     * @param     value             The char value to write.
     * @exception IOException on IO error.
     */
    public void writeChar(char value) throws IOException {
	if (char_index == CHAR_BUFFER_SIZE) {
	    flush();
	}
	if (DEBUG) {
	    dbPrint("wrote char " + value);
	}
	char_buffer[char_index++] = value;
    }

    /**
     * Writes a short value to the accumulator.
     * @param     value             The short value to write.
     * @exception IOException on IO error.
     */
    public void writeShort(int value) throws IOException {
	if (short_index == SHORT_BUFFER_SIZE) {
	    flush();
	}
	if (DEBUG) {
	    dbPrint("wrote short " + value);
	}
	short_buffer[short_index++] = (short) value;
    }

    /**
     * Writes a int value to the accumulator.
     * @param     value             The int value to write.
     * @exception IOException on IO error.
     */
    public void writeInt(int value) throws IOException {
	if (int_index == INT_BUFFER_SIZE) {
	    flush();
	}
	if (DEBUG) {
	    dbPrint("wrote int[HEX] " + value + "[0x" +
		    Integer.toHexString(value) + "]");
	}
	int_buffer[int_index++] = value;
    }

    /**
     * Writes a long value to the accumulator.
     * @param     value             The long value to write.
     * @exception IOException on IO error.
     */
    public void writeLong(long value) throws IOException {
	if (long_index == LONG_BUFFER_SIZE) {
	    flush();
	}
	if (DEBUG) {
	    dbPrint("wrote long " + value);
	}
	long_buffer[long_index++] = value;
    }

    /**
     * Writes a float value to the accumulator.
     * @param     value             The float value to write.
     * @exception IOException on IO error.
     */
    public void writeFloat(float value) throws IOException {
	if (float_index == FLOAT_BUFFER_SIZE) {
	    flush();
	}
	if (DEBUG) {
	    dbPrint("wrote float " + value);
	}
	float_buffer[float_index++] = value;
    }

    /**
     * Writes a double value to the accumulator.
     * @param     value             The double value to write.
     * @exception IOException on IO error.
     */
    public void writeDouble(double value) throws IOException {
	if (double_index == DOUBLE_BUFFER_SIZE) {
	    flush();
	}
	if (DEBUG) {
	    dbPrint("wrote double " + value);
	}
	double_buffer[double_index++] = value;
    }

    /**
     * Sends out handles as normal int's. Also checks if we
     * need to send out a reset first.
     * @param v		the handle to be written
     * @exception IOException	gets thrown when an IO error occurs.
     */
    private void writeHandle(int v) throws IOException {
        if (resetPending) {
                writeInt(RESET_HANDLE);
		if (DEBUG) {
		    dbPrint("wrote a RESET");
		}
                resetPending = false;
        }

        // treating handles as normal int's --N
        writeInt(v);
	if (DEBUG) {
	    dbPrint("wrote handle " + v);
	}
    }

    /**
     * @inheritDoc
     */
    public void writeBytes(String s) throws IOException {

	if (s == null) return;

	byte[] bytes = s.getBytes();
	int len = bytes.length;
	writeInt(len);
	for (int i = 0; i < len; i++) {
	    writeByte(bytes[i]);
	}
    }

    /**
     * @inheritDoc
     */
    public void writeChars(String s) throws IOException {

	if (s == null) return;

	int len = s.length();
	writeInt(len);
	for (int i = 0; i < len; i++) {
	    writeChar(s.charAt(i));
	}
    }

    /**
     * @inheritDoc
     */
    public void writeArray(boolean[] ref, int off, int len) throws IOException {
	if(writeArrayHeader(ref, classBooleanArray, len, false)) {
	    writeArray(ref, off, len, TYPE_BOOLEAN);
	}
    }

    /**
     * @inheritDoc
     */
    public void writeArray(byte[] ref, int off, int len) throws IOException {
	if(writeArrayHeader(ref, classByteArray, len, false)) {
	    writeArray(ref, off, len, TYPE_BYTE);
	}
    }

    /**
     * @inheritDoc
     */
    public void writeArray(short[] ref, int off, int len) throws IOException {
	if(writeArrayHeader(ref, classShortArray, len, false)) {
	    writeArray(ref, off, len, TYPE_SHORT);
	}
    }

    /**
     * @inheritDoc
     */
    public void writeArray(char[] ref, int off, int len) throws IOException {
	if(writeArrayHeader(ref, classCharArray, len, false)) {
	    writeArray(ref, off, len, TYPE_CHAR);
	}
    }

    /**
     * @inheritDoc
     */
    public void writeArray(int[] ref, int off, int len) throws IOException {
	if(writeArrayHeader(ref, classIntArray, len, false)) {
	    writeArray(ref, off, len, TYPE_INT);
	}
    }

    /**
     * @inheritDoc
     */
    public void writeArray(long[] ref, int off, int len) throws IOException {
	if(writeArrayHeader(ref, classLongArray, len, false)) {
	    writeArray(ref, off, len, TYPE_LONG);
	}
    }

    /**
     * @inheritDoc
     */
    public void writeArray(float[] ref, int off, int len) throws IOException {
	if(writeArrayHeader(ref, classFloatArray, len, false)) {
	    writeArray(ref, off, len, TYPE_FLOAT);
	}
    }

    /**
     * @inheritDoc
     */
    public void writeArray(double[] ref, int off, int len) throws IOException {
	if(writeArrayHeader(ref, classDoubleArray, len, false)) {
	    writeArray(ref, off, len, TYPE_DOUBLE);
	}
    }

    /**
     * @inheritDoc
     */
    public void writeArray(Object[] ref, int off, int len) throws IOException {
	Class clazz = ref.getClass();
	if (writeArrayHeader(ref, clazz, len, false)) {
	    for (int i = off; i < off + len; i++) {
		writeObject(ref[i]);
	    }
	}
    }

    /**
     * Writes a type or a handle.
     * If <code>ref</code> has been written before, this method writes its handle
     * and returns <code>true</code>. If not, its type is written, a new handle is
     * associated with it, and <code>false</code> is returned.
     *
     * @param ref		the object that is going to be put on the stream
     * @param clazz		the <code>Class</code> representing the type of <code>ref</code>
     * @exception IOException	gets thrown when an IO error occurs.
     */
    private boolean writeTypeHandle(Object ref, Class clazz) throws IOException {
	int handle = references.find(ref);

	if (handle != 0) {
	    writeHandle(handle);
	    return true;
	}

	writeType(clazz);

	handle = next_handle++;
	references.put(ref, handle);
	if (DEBUG) {
	    dbPrint("writeTypeHandle: references[" + handle + "] = " + (ref == null ? "null" : ref));
	}

	return false;
    }

    /**
     * Writes a handle or an array header, depending on wether a cycle should be and was
     * detected. If a cycle was detected, it returns <code>false</code>, otherwise <code>true</code>.
     * The array header consists of a type and a length.
     * @param ref		the array to be written
     * @param clazz		the <code>Class</code> representing the array type
     * @param len		the number of elements to be written
     * @param doCycleCheck	set when cycles should be detected
     * @exception IOException	gets thrown when an IO error occurs.
     * @return <code>true</code> if no cycle was or should be detected (so that the array
     * should be written).
     */
    private boolean writeArrayHeader(Object ref, Class clazz, int len, boolean doCycleCheck)
	throws IOException {

	if (ref == null) {
	    writeHandle(NUL_HANDLE);
	    return false;
	}

	if (doCycleCheck) {
	    /* A complete array. Do cycle/duplicate detection */
	    if (writeTypeHandle(ref, clazz)) {
		return false;
	    }
	} else {
	    writeType(clazz);
	}

	writeInt(len);

	if (DEBUG) {
	    dbPrint("writeArrayHeader " + clazz.getName() + " length = " + len);
	}
	return true;
    }

    /**
     * Writes an array, but possibly only a handle.
     * @param ref		the array to be written
     * @param arrayClazz	the <code>Class</code> representing the array type
     * @param unshared		set when no cycle detection check shoud be done
     * @exception IOException	gets thrown when an IO error occurs.
     */
    private void writeArray(Object ref, Class arrayClass, boolean unshared) throws IOException {
	if (false) {
	} else if (arrayClass == classByteArray) {
	    byte[] a = (byte[])ref;
	    int len = a.length;
	    if(writeArrayHeader(a, arrayClass, len, ! unshared)) {
		writeArray(a, 0, len, TYPE_BYTE);
	    }
	} else if (arrayClass == classIntArray) {
	    int[] a = (int[])ref;
	    int len = a.length;
	    if(writeArrayHeader(a, arrayClass, len, !unshared)) {
		writeArray(a, 0, len, TYPE_INT);
	    }
	} else if (arrayClass == classBooleanArray) {
	    boolean[] a = (boolean[])ref;
	    int len = a.length;
	    if(writeArrayHeader(a, arrayClass, len, ! unshared)) {
		writeArray(a, 0, len, TYPE_BOOLEAN);
	    }
	} else if (arrayClass == classDoubleArray) {
	    double[] a = (double[])ref;
	    int len = a.length;
	    if(writeArrayHeader(a, arrayClass, len, ! unshared)) {
		writeArray(a, 0, len, TYPE_DOUBLE);
	    }
	} else if (arrayClass == classCharArray) {
	    char[] a = (char[])ref;
	    int len = a.length;
	    if(writeArrayHeader(a, arrayClass, len, ! unshared)) {
		writeArray(a, 0, len, TYPE_CHAR);
	    }
	} else if (arrayClass == classShortArray) {
	    short[] a = (short[])ref;
	    int len = a.length;
	    if(writeArrayHeader(a, arrayClass, len, ! unshared)) {
		writeArray(a, 0, len, TYPE_SHORT);
	    }
	} else if (arrayClass == classLongArray) {
	    long[] a = (long[])ref;
	    int len = a.length;
	    if(writeArrayHeader(a, arrayClass, len, ! unshared)) {
		writeArray(a, 0, len, TYPE_LONG);
	    }
	} else if (arrayClass == classFloatArray) {
	    float[] a = (float[])ref;
	    int len = a.length;
	    if(writeArrayHeader(a, arrayClass, len, ! unshared)) {
		writeArray(a, 0, len, TYPE_FLOAT);
	    }
	} else {
	    Object[] a = (Object[])ref;
	    int len = a.length;
	    if(writeArrayHeader(a, arrayClass, len, ! unshared)) {
		for (int i = 0; i < len; i++) {
		    writeObject(a[i]);
		}
	    }
	}
    }

    /**
     * Adds the type represented by <code>clazz</code> to the type
     * table and returns its number.
     * @param clazz	represents the type to be added
     * @return		the type number.
     */
    private int newType(Class clazz) {
	int type_number = next_type++;

	type_number = (type_number | TYPE_BIT);
	types.put(clazz, type_number);

	return type_number;
    }

    /**
     * Writes a type number, and, when new, a type name to the output stream.
     * @param clazz		the clazz to be written.
     * @exception IOException	gets thrown when an IO error occurs.
     */
    private void writeType(Class clazz) throws IOException {
	int type_number = types.find(clazz);

	if (type_number != 0) {
	    writeHandle(type_number);	// TYPE_BIT is set, receiver sees it

	    if(DEBUG) {
		dbPrint("wrote type number 0x" + Integer.toHexString(type_number));
	    }
	    return;
	}

	type_number = newType(clazz);
	writeHandle(type_number);	// TYPE_BIT is set, receiver sees it
	if(DEBUG) {
	    dbPrint("wrote NEW type number 0x"
		    + Integer.toHexString(type_number) + " type " + clazz.getName());
	}
	writeUTF(clazz.getName());
    }

    /**
     * Writes a (new or old) handle for object <code>ref</code> to the output stream.
     * Returns 1 if the object is new, -1 if not.
     * @param ref		the object whose handle is to be written
     * @exception IOException	gets thrown when an IO error occurs.
     * @return			1 if it is a new object, -1 if it is not.
     */
    public int writeKnownObjectHeader(Object ref) throws IOException {

	if (ref == null) {
	    writeHandle(NUL_HANDLE);
	    return 0;
	}

	int handle = references.find(ref);

	if (handle == 0) {
	    Class clazz = ref.getClass();
	    handle = next_handle++;
	    if(DEBUG) {
		dbPrint("writeKnownObjectHeader -> writing NEW object, class = " + clazz.getName());
	    }
	    references.put(ref, handle);
	    writeType(clazz);
	    return 1;
	}

	if(DEBUG) {
	    dbPrint("writeKnownObjectHeader -> writing OLD HANDLE " + handle);
	}
	writeHandle(handle);
	return -1;
    }

    /**
     * Writes the serializable fields of an object <code>ref</code> using the type
     * information <code>t</code>.
     *
     * @param t		the type info for object <code>ref</code>
     * @param ref	the object of which the fields are to be written
     *
     * @exception IOException		 when an IO error occurs
     * @exception IllegalAccessException when access to a field is denied.
     */
    private void alternativeDefaultWriteObject(AlternativeTypeInfo t, Object ref) throws IOException, IllegalAccessException {
	int temp = 0;
	int i;

	if (DEBUG) {
	    dbPrint("alternativeDefaultWriteObject, class = " + t.clazz.getName());
	}
	for (i=0;i<t.double_count;i++)    writeDouble(t.serializable_fields[temp++].getDouble(ref));
	for (i=0;i<t.long_count;i++)      writeLong(t.serializable_fields[temp++].getLong(ref));
	for (i=0;i<t.float_count;i++)     writeFloat(t.serializable_fields[temp++].getFloat(ref));
	for (i=0;i<t.int_count;i++)       writeInt(t.serializable_fields[temp++].getInt(ref));
	for (i=0;i<t.short_count;i++)     writeShort(t.serializable_fields[temp++].getShort(ref));
	for (i=0;i<t.char_count;i++)      writeChar(t.serializable_fields[temp++].getChar(ref));
	for (i=0;i<t.byte_count;i++)      writeByte(t.serializable_fields[temp++].getByte(ref));
	for (i=0;i<t.boolean_count;i++)   writeBoolean(t.serializable_fields[temp++].getBoolean(ref));
	for (i=0;i<t.reference_count;i++) writeObject(t.serializable_fields[temp++].get(ref));
    }


    /**
     * Serializes an object <code>ref</code> using the type information <code>t</code>.
     *
     * @param t		the type info for object <code>ref</code>
     * @param ref	the object of which the fields are to be written
     *
     * @exception IOException		 when an IO error occurs
     * @exception IllegalAccessException when access to a field or <code>writeObject</code>
     * method is denied.
     */
    private void alternativeWriteObject(AlternativeTypeInfo t, Object ref) throws IOException, IllegalAccessException {
	if (t.superSerializable) {
	    alternativeWriteObject(t.alternativeSuperInfo, ref);
	}

	if (t.hasWriteObject) {
	    current_level = t.level;
	    try {
		if (DEBUG) {
		    dbPrint("invoking writeObject() of class " + t.clazz.getName());
		}
		t.invokeWriteObject(ref, this);
		if (DEBUG) {
		    dbPrint("done with writeObject() of class " + t.clazz.getName());
		}
	    } catch (java.lang.reflect.InvocationTargetException e) {
		if (DEBUG) {
		    dbPrint("Caught exception: " + e);
		    e.printStackTrace();
		    dbPrint("now rethrow as IllegalAccessException ...");
		}
		throw new IllegalAccessException("writeObject method: " + e);
	    }
	    return;
	}

	alternativeDefaultWriteObject(t, ref);
    }

    /**
     * Push the notions of <code>current_object</code>, <code>current_level</code>,
     * and <code>current_putfield</code> on their stacks, and set new ones.
     * @param ref	the new <code>current_object</code> notion
     * @param level	the new <code>current_level</code> notion
     */
    public void push_current_object(Object ref, int level) {
	if (stack_size >= max_stack_size) {
	    max_stack_size = 2 * max_stack_size + 10;
	    Object[] new_o_stack = new Object[max_stack_size];
	    int[] new_l_stack = new int[max_stack_size];
	    ImplPutField[] new_p_stack = new ImplPutField[max_stack_size];
	    for (int i = 0; i < stack_size; i++) {
		new_o_stack[i] = object_stack[i];
		new_l_stack[i] = level_stack[i];
		new_p_stack[i] = putfield_stack[i];
	    }
	    object_stack = new_o_stack;
	    level_stack = new_l_stack;
	    putfield_stack = new_p_stack;
	}
	object_stack[stack_size] = current_object;
	level_stack[stack_size] = current_level;
	putfield_stack[stack_size] = current_putfield;
	stack_size++;
	current_object = ref;
	current_level = level;
	current_putfield = null;
    }

    /**
     * Pop the notions of <code>current_object</code>, <code>current_level</code>,
     * and <code>current_putfield</code> from their stacks.
     */
    public void pop_current_object() {
	stack_size--;
	current_object = object_stack[stack_size];
	current_level = level_stack[stack_size];
	current_putfield = putfield_stack[stack_size];
    }

    /**
     * This method takes care of writing the serializable fields of the parent object.
     * and also those of its parent objects.
     * It gets called by IOGenerator-generated code when an object
     * has a superclass that is serializable but not Ibis serializable.
     *
     * @param ref	the object with a non-Ibis-serializable parent object
     * @param classname	the name of the superclass
     * @exception IOException	gets thrown on IO error
     */
    public void writeSerializableObject(Object ref, String classname) throws IOException {
	AlternativeTypeInfo t;
	try {
	    t = AlternativeTypeInfo.getAlternativeTypeInfo(classname);
	} catch(ClassNotFoundException e) {
	    throw new SerializationError("Internal error", e);
	}
	try {
	    push_current_object(ref, 0);
	    alternativeWriteObject(t, ref);
	    pop_current_object();
	} catch (IllegalAccessException e) {
	    if (DEBUG) {
		dbPrint("Caught exception: " + e);
		e.printStackTrace();
		dbPrint("now rethrow as java.io.NotSerializableException ...");
	    }
	    throw new java.io.NotSerializableException("Serializable failed for : " + classname);
	}
    }

    /**
     * This method takes care of writing the serializable fields of the parent object.
     * and also those of its parent objects.
     *
     * @param ref	the object with a non-Ibis-serializable parent object
     * @param clazz	the superclass
     * @exception IOException	gets thrown on IO error
     */
    private void writeSerializableObject(Object ref, Class clazz) throws IOException {
	AlternativeTypeInfo t = AlternativeTypeInfo.getAlternativeTypeInfo(clazz);
	try {
	    push_current_object(ref, 0);
	    alternativeWriteObject(t, ref);
	    pop_current_object();
	} catch (IllegalAccessException e) {
		if (DEBUG) {
		    dbPrint("Caught exception: " + e);
		    e.printStackTrace();
		    dbPrint("now rethrow as java.io.NotSerializableException ...");
		}
	    throw new java.io.NotSerializableException("Serializable failed for : " + clazz.getName());
	}
    }

    /**
     * Writes a <code>String</code> object. This is a special case, because strings
     * are written as an UTF.
     *
     * @param ref		the string to be written
     * @exception IOException	gets thrown on IO error
     */
    public void writeString(String ref) throws IOException {
	if (ref == null) {
	    if (DEBUG) {
		dbPrint("writeString: --> null");
	    }
	    writeHandle(NUL_HANDLE);
	    return;
	}

	int handle = references.find(ref);
	if (handle == 0) {
	    handle = next_handle++;
	    references.put(ref, handle);
	    writeType(java.lang.String.class);
	    if (DEBUG) {
		dbPrint("writeString: " + ref);
	    }
	    writeUTF(ref);
	} else {
	    if (DEBUG) {
		dbPrint("writeString: duplicate handle " + handle + " string = " + ref);
	    }
	    writeHandle(handle);
	}
    }

    /**
     * Write objects and arrays.
     * Duplicates are deteced when this call is used.
     * The replacement mechanism is implemented here as well.
     * We cannot redefine <code>writeObject</code>, because it is final in
     * <code>ObjectOutputStream</code>. The trick for Ibis serialization is to have the
     * <code>ObjectOutputStream</code> be initialized with its parameter-less constructor.
     * This will cause its <code>writeObject</code> to call <code>writeObjectOverride</code>
     * instead of doing its own thing.
     *
     * @param ref the object to be written
     * @exception <code>IOException</code> is thrown when an IO error occurs.
    */
    public void writeObjectOverride(Object ref) throws IOException {
	/*
	 * ref < 0:	type
	 * ref = 0:	null ptr
	 * ref > 0:	handle
	 */

	if (ref == null) {
	    writeHandle(NUL_HANDLE);
	    return;
	}
	/* TODO: deal with writeReplace! This should be done before
	   looking up the handle. If we don't want to do runtime
	   inspection, this should probably be handled somehow in
	   IOGenerator.
	   Note that the needed info is available in AlternativeTypeInfo,
	   but we don't want to use that when we have ibis.io.Serializable.
	   */

	if (replacer != null) {
	    ref = replacer.replace(ref);
	}
	int handle = references.find(ref);

	if (handle == 0) {
	    Class clazz = ref.getClass();
	    if(DEBUG) {
		dbPrint("start writeObject of class " + clazz.getName() + " handle = " + next_handle);
	    }

	    if (clazz.isArray()) {
		writeArray(ref, clazz, false);
	    } else {
		handle = next_handle++;
		references.put(ref, handle);
		writeType(clazz);
		if (clazz == java.lang.String.class) {
		    /* EEK this is not nice !! */
		    writeUTF((String)ref);
		} else if (clazz == java.lang.Class.class) {
		    /* EEK this is not nice !! */
		    writeUTF(((Class)ref).getName());
		} else if (IbisSerializationInputStream.isIbisSerializable(clazz)) {
		    ((ibis.io.Serializable)ref).generated_WriteObject(this);
		} else if (ref instanceof java.io.Externalizable) {
		    push_current_object(ref, 0);
		    ((java.io.Externalizable) ref).writeExternal(this);
		    pop_current_object();
		} else if (ref instanceof java.io.Serializable) {
		    writeSerializableObject(ref, clazz);
		} else { 
		    throw new java.io.NotSerializableException("Not Serializable : " + clazz.getName());
		}
	    }
	    if (DEBUG) {
		dbPrint("finished writeObject of class " + clazz.getName());
	    }
	} else {
	    if(DEBUG) {
		dbPrint("writeObject: duplicate handle " + handle + " class = " + ref.getClass());
	    }
	    writeHandle(handle);
	}
    }

    /**
     * @inheritDoc
     */
    public void writeUnshared(Object ref) throws IOException {
	if (ref == null) {
	    writeHandle(NUL_HANDLE);
	    return;
	}
	/* TODO: deal with writeReplace! This should be done before
	   looking up the handle. If we don't want to do runtime
	   inspection, this should probably be handled somehow in
	   IOGenerator.
	   Note that the needed info is available in AlternativeTypeInfo,
	   but we don't want to use that when we have ibis.io.Serializable.
	*/
	Class clazz = ref.getClass();
	if(DEBUG) {
	    dbPrint("start writeUnshared of class " + clazz.getName() + " handle = " + next_handle);
	}

	if (clazz.isArray()) {
	    writeArray(ref, clazz, true);
	} else {
	    writeType(clazz);
	    if (clazz == java.lang.String.class) {
		/* EEK this is not nice !! */
		writeUTF((String)ref);
	    } else if (clazz == java.lang.Class.class) {
		/* EEK this is not nice !! */
		writeUTF(((Class)ref).getName());
	    } else if (ref instanceof java.io.Externalizable) {
		push_current_object(ref, 0);
		((java.io.Externalizable) ref).writeExternal(this);
		pop_current_object();
	    } else if (IbisSerializationInputStream.isIbisSerializable(clazz)) {
		((ibis.io.Serializable)ref).generated_WriteObject(this);
	    } else if (ref instanceof java.io.Serializable) {
		writeSerializableObject(ref, clazz);
	    } else {
		throw new RuntimeException("Not Serializable : " + clazz.getName());
	    }
	}
	if(DEBUG) {
	    dbPrint("finished writeUnshared of class " + clazz.getName() + " handle = " + next_handle);
	}
    }

    /**
     * @inheritDoc
     */
    public void close() throws IOException {
	flush();
	out.close();
    }

    /**
     * @inheritDoc
     */
    public void useProtocolVersion(int version) {
	/* ignored. */
    }

    /**
     * @inheritDoc
     */
    protected void writeStreamHeader() {
	/* ignored. */
    }

    /**
     * @inheritDoc
     */
    protected void writeClassDescriptor(ObjectStreamClass desc) {
	/* ignored */
    }

    /* annotateClass does not have to be redefined: it is empty in the
       ObjectOutputStream implementation.
    */

    /**
     * @inheritDoc
     */
    public void writeFields() throws IOException {
	if (current_putfield == null) {
	    throw new NotActiveException("no PutField object");
	}
	current_putfield.writeFields();
    }

    /**
     * @inheritDoc
     */
    public PutField putFields() throws IOException {
	if (current_putfield == null) {
	    if (current_object == null) {
		throw new NotActiveException("not in writeObject");
	    }
	    Class clazz = current_object.getClass();
	    AlternativeTypeInfo t = AlternativeTypeInfo.getAlternativeTypeInfo(clazz);
	    current_putfield = new ImplPutField(t);
	}
	return current_putfield;
    }

    /**
     * The Ibis serialization implementation of <code>PutField</code>.
     */
    private class ImplPutField extends PutField {
	private double[]  doubles;
	private long[]	  longs;
	private int[]	  ints;
	private float[]   floats;
	private short[]   shorts;
	private char[]    chars;
	private byte[]	  bytes;
	private boolean[] booleans;
	private Object[]  references;
	private AlternativeTypeInfo t;

	ImplPutField(AlternativeTypeInfo t) {
	    doubles = new double[t.double_count];
	    longs = new long[t.long_count];
	    ints = new int[t.int_count];
	    shorts = new short[t.short_count];
	    floats = new float[t.float_count];
	    chars = new char[t.char_count];
	    bytes = new byte[t.byte_count];
	    booleans = new boolean[t.boolean_count];
	    references = new Object[t.reference_count];
	    this.t = t;
	}

	public void put(String name, boolean value)
	    throws IllegalArgumentException {
	    booleans[t.getOffset(name, Boolean.TYPE)] = value;
	}

	public void put(String name, char value)
	    throws IllegalArgumentException {
	    chars[t.getOffset(name, Character.TYPE)] = value;
	}

	public void put(String name, byte value)
	    throws IllegalArgumentException {
	    bytes[t.getOffset(name, Byte.TYPE)] = value;
	}

	public void put(String name, short value)
	    throws IllegalArgumentException {
	    shorts[t.getOffset(name, Short.TYPE)] = value;
	}

	public void put(String name, int value)
	    throws IllegalArgumentException {
	    ints[t.getOffset(name, Integer.TYPE)] = value;
	}

	public void put(String name, long value)
	    throws IllegalArgumentException {
	    longs[t.getOffset(name, Long.TYPE)] = value;
	}

	public void put(String name, float value)
	    throws IllegalArgumentException {
	    floats[t.getOffset(name, Float.TYPE)] = value;
	}

	public void put(String name, double value)
	    throws IllegalArgumentException {
	    doubles[t.getOffset(name, Double.TYPE)] = value;
	}

	public void put(String name, Object value) {
	    references[t.getOffset(name, Object.class)] = value;
	}

	public void write(ObjectOutput o) throws IOException {
	    for (int i = 0; i < t.double_count; i++) o.writeDouble(doubles[i]);
	    for (int i = 0; i < t.float_count; i++) o.writeFloat(floats[i]);
	    for (int i = 0; i < t.long_count; i++) o.writeLong(longs[i]);
	    for (int i = 0; i < t.int_count; i++) o.writeInt(ints[i]);
	    for (int i = 0; i < t.short_count; i++) o.writeShort(shorts[i]);
	    for (int i = 0; i < t.char_count; i++) o.writeChar(chars[i]);
	    for (int i = 0; i < t.byte_count; i++) o.writeByte(bytes[i]);
	    for (int i = 0; i < t.boolean_count; i++) o.writeBoolean(booleans[i]);
	    for (int i = 0; i < t.reference_count; i++) o.writeObject(references[i]);
	}


	void writeFields() throws IOException {
	    for (int i = 0; i < t.double_count; i++) writeDouble(doubles[i]);
	    for (int i = 0; i < t.float_count; i++) writeFloat(floats[i]);
	    for (int i = 0; i < t.long_count; i++) writeLong(longs[i]);
	    for (int i = 0; i < t.int_count; i++) writeInt(ints[i]);
	    for (int i = 0; i < t.short_count; i++) writeShort(shorts[i]);
	    for (int i = 0; i < t.char_count; i++) writeChar(chars[i]);
	    for (int i = 0; i < t.byte_count; i++) writeByte(bytes[i]);
	    for (int i = 0; i < t.boolean_count; i++) writeBoolean(booleans[i]);
	    for (int i = 0; i < t.reference_count; i++) writeObject(references[i]);
	}
    }

    /**
     * This method writes the serializable fields of object <code>ref</code> at the
     * level indicated by <code>depth</code> * (see the explanation at the declaration
     * of the <code>current_level</code> field.
     * It gets called from IOGenerator-generated code, when a parent object
     * is serializable but not Ibis serializable.
     *
     * @param ref		the object of which serializable fields must be written
     * @param depth		an indication of the current "view" of the object
     * @exception IOException	gets thrown when an IO error occurs.
     */
    public void defaultWriteSerializableObject(Object ref, int depth) throws IOException {
	Class clazz = ref.getClass();
	AlternativeTypeInfo t = AlternativeTypeInfo.getAlternativeTypeInfo(clazz);

	/*  Find the type info corresponding to the current invocation.
	    See the invokeWriteObject invocation in alternativeWriteObject.
	    */
	while (t.level > depth) {
	    t = t.alternativeSuperInfo;
	}
	try {
	    alternativeDefaultWriteObject(t, ref);
	} catch(IllegalAccessException e) {
		if (DEBUG) {
		    dbPrint("Caught exception: " + e);
		    e.printStackTrace();
		    dbPrint("now rethrow as NotSerializableException ...");
		}
	    throw new NotSerializableException("illegal access" + e);
	}
    }

    /**
     * @inheritDoc
     */
    public void defaultWriteObject() throws IOException, NotActiveException {
	if (current_object == null) {
	    throw new NotActiveException("defaultWriteObject without a current object");
	}

	Object ref = current_object;
	Class clazz = ref.getClass();

	if (IbisSerializationInputStream.isIbisSerializable(clazz)) {
	    /* Note that this will take the generated_DefaultWriteObject of the
	       dynamic type of ref. The current_level variable actually indicates
	       which instance of generated_DefaultWriteObject should do some work.
	       */
	    if (DEBUG) {
		dbPrint("generated_DefaultWriteObject, class = " + clazz.getName() + ", level = " + current_level);
	    }
	    ((ibis.io.Serializable)ref).generated_DefaultWriteObject(this, current_level);
	} else if (ref instanceof java.io.Serializable) {
	    AlternativeTypeInfo t = AlternativeTypeInfo.getAlternativeTypeInfo(clazz);

	    /*	Find the type info corresponding to the current invocation.
		See the invokeWriteObject invocation in alternativeWriteObject.
		*/
	    while (t.level > current_level) {
		t = t.alternativeSuperInfo;
	    }
	    try {
		alternativeDefaultWriteObject(t, ref);
	    } catch(IllegalAccessException e) {
		if (DEBUG) {
		    dbPrint("Caught exception: " + e);
		    e.printStackTrace();
		    dbPrint("now rethrow as NotSerializableException ...");
		}
		throw new NotSerializableException("illegal access" + e);
	    }
	} else { 
	    throw new java.io.NotSerializableException("Not Serializable : " + clazz.getName());
	}
    }
}
