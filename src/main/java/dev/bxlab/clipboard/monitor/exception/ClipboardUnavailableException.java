package dev.bxlab.clipboard.monitor.exception;

/**
 * Thrown when the system clipboard is unavailable after retries.
 * <p>
 * This typically occurs when another application has locked the clipboard.
 */
public class ClipboardUnavailableException extends ClipboardException {

    /**
     * Creates a new clipboard unavailable exception.
     *
     * @param message error description
     */
    public ClipboardUnavailableException(String message) {
        super(message);
    }

    /**
     * Creates a new clipboard-unavailable exception with cause.
     *
     * @param message error description
     * @param cause   underlying cause
     */
    public ClipboardUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
