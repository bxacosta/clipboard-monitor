package dev.bxlab.clipboard.monitor;

import dev.bxlab.clipboard.monitor.util.TextUtils;

import java.time.Instant;
import java.util.Objects;

/**
 * Unknown or unsupported content from the clipboard.
 * <p>
 * Represents content that could not be classified as text, image, or files.
 * This typically happens when the clipboard contains a format not supported
 * by this library.
 *
 * <pre>{@code
 * UnknownContent content = new UnknownContent(hash, Instant.now());
 * System.out.println("Unknown content detected");
 * }</pre>
 *
 * @param hash      SHA-256 hash of the content
 * @param timestamp when the content was captured
 */
public record UnknownContent(
        String hash,
        Instant timestamp
) implements ClipboardContent {

    /**
     * Creates a new UnknownContent instance.
     *
     * @throws NullPointerException if hash or timestamp is null
     */
    public UnknownContent {
        Objects.requireNonNull(hash, "hash cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    @Override
    public ContentType type() {
        return ContentType.UNKNOWN;
    }

    @Override
    public long size() {
        return 0;
    }

    @Override
    public String toString() {
        return "UnknownContent{" +
                "hash='" + TextUtils.truncate(hash) + "'" +
                ", timestamp=" + timestamp +
                '}';
    }
}
