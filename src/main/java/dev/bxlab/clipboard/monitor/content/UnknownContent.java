package dev.bxlab.clipboard.monitor.content;

import dev.bxlab.clipboard.monitor.util.TextUtils;

import java.time.Instant;
import java.util.Objects;

/**
 * Unknown or unsupported clipboard content.
 *
 * @param hash      SHA-256 hash of the content
 * @param timestamp when the content was captured
 */
public record UnknownContent(
        String hash,
        Instant timestamp
) implements ClipboardContent {

    /**
     * Compact constructor with validation.
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
