package dev.bxlab.clipboard.monitor.exception;

/**
 * Base exception for clipboard-related errors.
 * <p>
 * All clipboard-specific exceptions extend this class.
 */
public class ClipboardException extends RuntimeException {

    /**
     * Creates a new clipboard exception with a message.
     *
     * @param message error description
     */
    public ClipboardException(String message) {
        super(message);
    }

    /**
     * Creates a new clipboard exception with a message and cause.
     *
     * @param message error description
     * @param cause   underlying cause
     */
    public ClipboardException(String message, Throwable cause) {
        super(message, cause);
    }
}
