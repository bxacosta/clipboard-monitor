package dev.bxlab.clipboard.monitor;

import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Immutable representation of clipboard content at a specific moment.
 * <p>
 * This is a sealed interface with four permitted implementations:
 * <ul>
 *   <li>{@link TextContent} - text content (plain text, HTML, RTF)</li>
 *   <li>{@link ImageContent} - image content (PNG, JPEG, BMP)</li>
 *   <li>{@link FilesContent} - list of files</li>
 *   <li>{@link UnknownContent} - unknown or unsupported content type</li>
 * </ul>
 * <p>
 * All implementations are records, ensuring immutability and thread-safety.
 *
 * <h2>Usage with Pattern Matching (Java 21)</h2>
 * <pre>{@code
 * ClipboardContent content = monitor.read();
 * switch (content) {
 *     case TextContent(var text, var hash, var ts) -> {
 *         System.out.println("Text: " + text);
 *     }
 *     case ImageContent(var img, _, _, var w, var h) -> {
 *         System.out.println("Image: " + w + "x" + h);
 *     }
 *     case FilesContent(var files, _, _, _) -> {
 *         files.forEach(f -> System.out.println("File: " + f));
 *     }
 *     case UnknownContent _ -> {
 *         System.out.println("Unknown content");
 *     }
 * }
 * }</pre>
 *
 * <h2>Usage with Convenience Methods</h2>
 * <pre>{@code
 * content.asText().ifPresent(text -> System.out.println("Text: " + text));
 * content.asImage().ifPresent(img -> processImage(img));
 * content.asFiles().ifPresent(files -> processFiles(files));
 * }</pre>
 *
 * @see TextContent
 * @see ImageContent
 * @see FilesContent
 * @see UnknownContent
 */
public sealed interface ClipboardContent
        permits TextContent, ImageContent, FilesContent, UnknownContent {

    /**
     * Returns the content type classification.
     *
     * @return content type
     */
    ContentType type();

    /**
     * Returns the SHA-256 hash for content identification.
     * <p>
     * The hash is used for change detection and duplicate prevention.
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
     * <p>
     * For text, this is the UTF-8 encoded length.
     * For images, this is width * height * 4 (ARGB).
     * For files, this is the total size of all files.
     * For unknown content, this is 0.
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
