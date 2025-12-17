package dev.bxlab.clipboard.monitor.exception;

/**
 * Thrown when clipboard content changes during a read operation.
 * <p>
 * This is a transient condition that typically resolves on retry.
 */
public class ClipboardChangedException extends ClipboardException {

    /**
     * Creates a new clipboard changed exception.
     *
     * @param message error description
     */
    public ClipboardChangedException(String message) {
        super(message);
    }

    /**
     * Creates a new clipboard changed exception with cause.
     *
     * @param message error description
     * @param cause   underlying cause
     */
    public ClipboardChangedException(String message, Throwable cause) {
        super(message, cause);
    }
}
