package dev.bxlab.clipboard.monitor.exception;

import lombok.Getter;

/**
 * Thrown when clipboard content exceeds the configured size limit.
 */
@Getter
public class ContentTooLargeException extends ClipboardException {

    /**
     * Actual content size in bytes.
     */
    private final long actualSize;

    /**
     * Maximum allowed size in bytes.
     */
    private final long maxSize;

    /**
     * Creates a new content too large exception.
     *
     * @param actualSize actual content size in bytes
     * @param maxSize    maximum allowed size in bytes
     */
    public ContentTooLargeException(long actualSize, long maxSize) {
        super(String.format("Content too large: %d bytes > %d bytes limit", actualSize, maxSize));
        this.actualSize = actualSize;
        this.maxSize = maxSize;
    }
}
