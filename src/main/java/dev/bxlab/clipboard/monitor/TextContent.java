package dev.bxlab.clipboard.monitor;

import dev.bxlab.clipboard.monitor.util.TextUtils;

import java.time.Instant;
import java.util.Objects;

/**
 * Text content from the clipboard.
 *
 * <pre>{@code
 * TextContent content = new TextContent("Hello World", hash, Instant.now(), 11);
 * System.out.println("Text: " + content.text());
 * System.out.println("Length: " + content.size() + " bytes");
 * }</pre>
 *
 * @param text      the text content (never null)
 * @param hash      SHA-256 hash of the content
 * @param timestamp when the content was captured
 * @param sizeBytes size of the text in UTF-8 bytes
 */
public record TextContent(
        String text,
        String hash,
        Instant timestamp,
        long sizeBytes
) implements ClipboardContent {

    /**
     * Creates a new TextContent instance.
     *
     * @throws NullPointerException     if any parameter is null
     * @throws IllegalArgumentException if sizeBytes is negative
     */
    public TextContent {
        Objects.requireNonNull(text, "text cannot be null");
        Objects.requireNonNull(hash, "hash cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes cannot be negative: " + sizeBytes);
        }
    }

    @Override
    public ContentType type() {
        return ContentType.TEXT;
    }

    @Override
    public long size() {
        return sizeBytes;
    }

    @Override
    public String toString() {
        return "TextContent{" +
                "text='" + TextUtils.truncate(text, 50) + "'" +
                ", hash='" + TextUtils.truncate(hash) + "'" +
                ", timestamp=" + timestamp +
                ", size=" + sizeBytes +
                '}';
    }
}
