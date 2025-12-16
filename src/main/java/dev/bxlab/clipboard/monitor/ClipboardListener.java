package dev.bxlab.clipboard.monitor;

/**
 * Listener for clipboard change notifications.
 * <p>
 * Implement this interface to receive callbacks when system clipboard content changes.
 *
 * <pre>{@code
 * ClipboardListener listener = content -> {
 *     System.out.println("New content: " + content.getType());
 * };
 * }</pre>
 */
@FunctionalInterface
public interface ClipboardListener {

    /**
     * Called when clipboard change is detected.
     * This method is called on a separate thread. Implementations must be thread-safe
     * if accessing shared resources.
     *
     * @param content new clipboard content
     */
    void onClipboardChange(ClipboardContent content);

    /**
     * Called when an error occurs during monitoring.
     * Default implementation does nothing. Override for custom error handling.
     *
     * @param error exception that occurred
     */
    default void onError(Exception error) {
    }
}
