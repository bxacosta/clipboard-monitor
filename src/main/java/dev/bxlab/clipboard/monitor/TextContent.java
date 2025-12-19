package dev.bxlab.clipboard.monitor;

import dev.bxlab.clipboard.monitor.util.TextUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

/**
 * Text content from the clipboard.
 * <p>
 * Represents plain text, HTML, RTF, or any other text-based content.
 * The actual text is stored as a String.
 *
 * <pre>{@code
 * TextContent content = new TextContent("Hello World", hash, Instant.now());
 * System.out.println("Text: " + content.text());
 * System.out.println("Length: " + content.size() + " bytes");
 * }</pre>
 *
 * @param text      the text content (never null)
 * @param hash      SHA-256 hash of the content
 * @param timestamp when the content was captured
 */
public record TextContent(
        String text,
        String hash,
        Instant timestamp
) implements ClipboardContent {

    /**
     * Creates a new TextContent instance.
     *
     * @throws NullPointerException if any parameter is null
     */
    public TextContent {
        Objects.requireNonNull(text, "text cannot be null");
        Objects.requireNonNull(hash, "hash cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    @Override
    public ContentType type() {
        return ContentType.TEXT;
    }

    @Override
    public long size() {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    @Override
    public String toString() {
        return "TextContent{" +
                "text='" + TextUtils.truncate(text, 50) + "'" +
                ", hash='" + TextUtils.truncate(hash) + "'" +
                ", timestamp=" + timestamp +
                ", size=" + size() +
                '}';
    }
}
