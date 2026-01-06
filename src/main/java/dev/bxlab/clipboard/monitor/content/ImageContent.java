package dev.bxlab.clipboard.monitor.content;

import dev.bxlab.clipboard.monitor.util.TextUtils;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Objects;

/**
 * Image content from the clipboard.
 *
 * @param image     the image content
 * @param hash      SHA-256 hash of the content
 * @param timestamp when the content was captured
 * @param width     image width in pixels
 * @param height    image height in pixels
 */
public record ImageContent(
        BufferedImage image,
        String hash,
        Instant timestamp,
        int width,
        int height
) implements ClipboardContent {

    /**
     * Compact constructor with validation.
     *
     * @throws NullPointerException     if image, hash, or timestamp is null
     * @throws IllegalArgumentException if width or height is not positive
     */
    public ImageContent {
        Objects.requireNonNull(image, "image cannot be null");
        Objects.requireNonNull(hash, "hash cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive: " + height);
        }
    }

    /**
     * Creates ImageContent with dimensions extracted from the image.
     *
     * @param image     the image content
     * @param hash      SHA-256 hash of the content
     * @param timestamp when the content was captured
     * @return new ImageContent instance
     * @throws NullPointerException if any parameter is null
     */
    public static ImageContent of(BufferedImage image, String hash, Instant timestamp) {
        Objects.requireNonNull(image, "image cannot be null");
        return new ImageContent(image, hash, timestamp, image.getWidth(), image.getHeight());
    }

    @Override
    public ContentType type() {
        return ContentType.IMAGE;
    }

    @Override
    public long size() {
        return (long) width * height * 4;
    }

    @Override
    public String toString() {
        return "ImageContent{" +
                "dimensions=" + width + "x" + height +
                ", hash='" + TextUtils.truncate(hash) + "'" +
                ", timestamp=" + timestamp +
                ", size=" + size() +
                '}';
    }
}
