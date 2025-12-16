package dev.bxlab.clipboard.monitor;

import lombok.Getter;

/**
 * Base exception for clipboard-related errors.
 */
public class ClipboardException extends RuntimeException {

    public ClipboardException(String message) {
        super(message);
    }

    public ClipboardException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Thrown when clipboard content exceeds the configured limit.
     */
    @Getter
    public static class ContentTooLargeException extends ClipboardException {
        private final long actualSize;
        private final long maxSize;

        public ContentTooLargeException(long actualSize, long maxSize) {
            super(String.format("Content too large: %d bytes > %d bytes limit", actualSize, maxSize));
            this.actualSize = actualSize;
            this.maxSize = maxSize;
        }
    }

    /**
     * Thrown when clipboard is unavailable after several retries.
     */
    public static class ClipboardUnavailableException extends ClipboardException {
        public ClipboardUnavailableException(String message) {
            super(message);
        }

        public ClipboardUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when clipboard changes during a read operation.
     */
    public static class ClipboardChangedException extends ClipboardException {
        public ClipboardChangedException(String message) {
            super(message);
        }

        public ClipboardChangedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
