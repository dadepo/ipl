package ibis.ipl;

/**
 * Signals an attempt to connect ports of different types.
 */
public class PortMismatchException extends IbisIOException {

    /**
     * Constructs a <code>PortMismatchException</code> with
     * <code>null</code> as its error detail message.
     */
    public PortMismatchException() {
	super();
    }

    /**
     * Constructs a <code>PortMismatchException</code> with
     * the specified detail message.
     *
     * @param s		the detail message
     */
    public PortMismatchException(String s) {
	super(s);
    }

    /**
     * Constructs a <code>PortMismatchException</code> with
     * the specified detail message and cause.
     *
     * @param s		the detail message
     * @param cause	the cause
     */
    public PortMismatchException(String s, Throwable cause) {
	super(s, cause);
    }

    /**
     * Constructs a <code>PortMismatchException</code> with
     * the specified cause.
     *
     * @param cause	the cause
     */
    public PortMismatchException(Throwable cause) {
	super(cause);
    }
}
