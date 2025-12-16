package dev.bxlab.clipboard.monitor;

import lombok.Getter;
import lombok.Singular;

import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Represents clipboard content at a given moment.
 * <p>
 * Immutable and thread-safe. Contains clipboard content along with metadata
 * like type, timestamp, and hash.
 *
 * <pre>{@code
 * ClipboardContent content = ...;
 *
 * switch (content.getType()) {
 *     case TEXT -> content.asText().ifPresent(System.out::println);
 *     case IMAGE -> content.asImage().ifPresent(this::processImage);
 *     case FILE_LIST -> content.asFileList().ifPresent(this::processFiles);
 *     case UNKNOWN -> System.out.println("Unknown content");
 * }
 * }</pre>
 */
@Getter
public final class ClipboardContent {

    private final ContentType type;
    private final Instant timestamp;
    private final String hash;
    private final long size;
    private final Object data;
    private final List<DataFlavor> flavors;
    private final byte[] rawBytes;

    @lombok.Builder
    private ClipboardContent(
            ContentType type,
            Instant timestamp,
            String hash,
            long size,
            Object data,
            @Singular List<DataFlavor> flavors,
            byte[] rawBytes) {
        this.type = java.util.Objects.requireNonNull(type, "type cannot be null");
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.hash = java.util.Objects.requireNonNull(hash, "hash cannot be null");
        this.size = size;
        this.data = data;
        this.flavors = flavors != null ? List.copyOf(flavors) : List.of();
        this.rawBytes = rawBytes;
    }

    public static class ClipboardContentBuilder {
        public ClipboardContentBuilder textData(String text) {
            this.data = text;
            this.type = ContentType.TEXT;
            return this;
        }

        public ClipboardContentBuilder imageData(BufferedImage image) {
            this.data = image;
            this.type = ContentType.IMAGE;
            return this;
        }

        public ClipboardContentBuilder fileListData(List<File> files) {
            this.data = List.copyOf(files);
            this.type = ContentType.FILE_LIST;
            return this;
        }

        public ClipboardContentBuilder rawBytes(byte[] rawBytes) {
            this.rawBytes = rawBytes != null ? rawBytes.clone() : null;
            return this;
        }
    }

    /**
     * Gets content as text.
     *
     * @return text if type is TEXT, empty otherwise
     */
    public Optional<String> asText() {
        return type == ContentType.TEXT && data instanceof String text
                ? Optional.of(text)
                : Optional.empty();
    }

    /**
     * Gets content as image.
     *
     * @return image if type is IMAGE, empty otherwise
     */
    public Optional<BufferedImage> asImage() {
        return type == ContentType.IMAGE && data instanceof BufferedImage image
                ? Optional.of(image)
                : Optional.empty();
    }

    /**
     * Gets content as file list.
     *
     * @return file list if type is FILE_LIST, empty otherwise
     */
    @SuppressWarnings("unchecked")
    public Optional<List<File>> asFileList() {
        return type == ContentType.FILE_LIST && data instanceof List<?>
                ? Optional.of((List<File>) data)
                : Optional.empty();
    }

    /**
     * Gets content as raw bytes.
     * For text, returns UTF-8 bytes. For images, may be null.
     * For files, returns concatenated paths in UTF-8.
     *
     * @return raw bytes of content, or empty array if unavailable
     */
    public byte[] asBytes() {
        if (rawBytes != null) {
            return rawBytes.clone();
        }
        if (data instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
        return new byte[0];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClipboardContent that = (ClipboardContent) o;
        return hash.equals(that.hash);
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }

    @Override
    public String toString() {
        return "ClipboardContent{" +
                "type=" + type +
                ", timestamp=" + timestamp +
                ", hash='" + hash.substring(0, Math.min(8, hash.length())) + "...'" +
                ", size=" + size +
                '}';
    }
}
