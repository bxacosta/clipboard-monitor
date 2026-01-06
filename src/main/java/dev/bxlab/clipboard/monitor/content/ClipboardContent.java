package dev.bxlab.clipboard.monitor.content;

import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Immutable representation of clipboard content at a specific moment.
 * <p>
 * Sealed interface with four implementations:
 * {@link TextContent}, {@link ImageContent}, {@link FilesContent}, {@link UnknownContent}.
 *
 * @see TextContent
 * @see ImageContent
 * @see FilesContent
 * @see UnknownContent
 */
public sealed interface ClipboardContent permits TextContent, ImageContent, FilesContent, UnknownContent {

    /**
     * Returns the content type classification.
     *
     * @return content type
     */
    ContentType type();

    /**
     * Returns the SHA-256 hash for content identification.
     *
     * @return hexadecimal hash string
     */
    String hash();

    /**
     * Returns when the content was captured.
     *
     * @return capture timestamp
     */
    Instant timestamp();

    /**
     * Returns the content size in bytes.
     *
     * @return size in bytes
     */
    long size();

    /**
     * Returns the text content if this is a {@link TextContent}.
     *
     * @return text content or empty if not text type
     */
    default Optional<String> asText() {
        return this instanceof TextContent t ? Optional.of(t.text()) : Optional.empty();
    }

    /**
     * Returns the image content if this is an {@link ImageContent}.
     *
     * @return image content or empty if not an image type
     */
    default Optional<BufferedImage> asImage() {
        return this instanceof ImageContent i ? Optional.of(i.image()) : Optional.empty();
    }

    /**
     * Returns the file list if this is a {@link FilesContent}.
     *
     * @return file list or empty if not files type
     */
    default Optional<List<File>> asFiles() {
        return this instanceof FilesContent f ? Optional.of(f.files()) : Optional.empty();
    }
}
