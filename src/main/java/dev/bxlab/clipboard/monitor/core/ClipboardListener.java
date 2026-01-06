package dev.bxlab.clipboard.monitor.core;

import dev.bxlab.clipboard.monitor.content.ClipboardContent;

/**
 * Listener for clipboard change notifications.
 */
@FunctionalInterface
public interface ClipboardListener {

    /**
     * Called when clipboard content changes.
     *
     * @param content new clipboard content
     */
    void onChange(ClipboardContent content);

    /**
     * Called when an error occurs during listener execution.
     *
     * @param error exception that occurred
     */
    default void onError(Exception error) {
    }
}
