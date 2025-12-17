package dev.bxlab.clipboard.monitor;

import dev.bxlab.clipboard.monitor.util.LogUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable representation of clipboard content at a specific moment.
 * <p>
 * Contains the actual content data along with metadata like type, timestamp, and hash.
 * Thread-safe due to immutability.
 *
 * <pre>{@code
 * ClipboardContent content = ClipboardContent.builder()
 *         .textData("Hello World")
 *         .hash(hashValue)
 *         .size(11)
 *         .build();
 *
 * switch (content.getType()) {
 *     case TEXT -> content.asText().ifPresent(System.out::println);
 *     case IMAGE -> content.asImage().ifPresent(this::processImage);
 *     case FILE_LIST -> content.asFileList().ifPresent(this::processFiles);
 *     case UNKNOWN -> log.warn("Unknown content type");
 * }
 * }</pre>
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class ClipboardContent {

    /**
     * Content type classification.
     */
    private final ContentType type;

    /**
     * When the content was captured.
     */
    private final Instant timestamp;

    /**
     * SHA-256 hash for content identification.
     */
    @EqualsAndHashCode.Include
    private final String hash;

    /**
     * Content size in bytes.
     */
    private final long size;

    /**
     * The actual content data (String, BufferedImage, or List of File).
     */
    private final Object data;

    /**
     * Available data flavors from the clipboard.
     */
    private final List<DataFlavor> flavors;

    /**
     * Raw byte representation of the content.
     */
    private final byte[] rawBytes;

    private ClipboardContent(Builder builder) {
        this.type = Objects.requireNonNull(builder.type, "type cannot be null");
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.hash = Objects.requireNonNull(builder.hash, "hash cannot be null");
        this.size = builder.size;
        this.data = builder.data;
        this.flavors = builder.flavors != null ? List.copyOf(builder.flavors) : List.of();
        this.rawBytes = builder.rawBytes;
    }

    /**
     * Creates a new builder.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns content as text if type is TEXT.
     *
     * @return text content or empty if not text type
     */
    public Optional<String> asText() {
        return type == ContentType.TEXT && data instanceof String text
                ? Optional.of(text)
                : Optional.empty();
    }

    /**
     * Returns content as image if type is IMAGE.
     *
     * @return image content or empty if not image type
     */
    public Optional<BufferedImage> asImage() {
        return type == ContentType.IMAGE && data instanceof BufferedImage image
                ? Optional.of(image)
                : Optional.empty();
    }

    /**
     * Returns content as file list if type is FILE_LIST.
     *
     * @return file list or empty if not file list type
     */
    @SuppressWarnings("unchecked")
    public Optional<List<File>> asFileList() {
        return type == ContentType.FILE_LIST && data instanceof List<?>
                ? Optional.of((List<File>) data)
                : Optional.empty();
    }

    /**
     * Returns content as raw bytes.
     * <p>
     * For text, returns UTF-8 encoded bytes.
     * For files, returns concatenated paths in UTF-8.
     * For images, returns stored raw bytes or empty array.
     *
     * @return raw bytes (defensive copy) or empty array if unavailable
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
    public String toString() {
        return "ClipboardContent{" +
                "type=" + type +
                ", timestamp=" + timestamp +
                ", hash='" + LogUtils.truncateHash(hash) + "'" +
                ", size=" + size +
                '}';
    }

    /**
     * Builder for creating ClipboardContent instances.
     * <p>
     * Use type-specific methods ({@link #textData}, {@link #imageData}, {@link #fileListData})
     * to set content, which automatically configures the correct type.
     */
    public static final class Builder {
        private ContentType type;
        private Instant timestamp;
        private String hash;
        private long size;
        private Object data;
        private List<DataFlavor> flavors;
        private byte[] rawBytes;

        private Builder() {
        }

        /**
         * Sets text content and automatically sets type to TEXT.
         *
         * @param text text content
         * @return this builder
         * @throws NullPointerException if text is null
         */
        public Builder textData(String text) {
            Objects.requireNonNull(text, "text cannot be null");
            this.data = text;
            this.type = ContentType.TEXT;
            return this;
        }

        /**
         * Sets image content and automatically sets type to IMAGE.
         *
         * @param image image content
         * @return this builder
         * @throws NullPointerException if image is null
         */
        public Builder imageData(BufferedImage image) {
            Objects.requireNonNull(image, "image cannot be null");
            this.data = image;
            this.type = ContentType.IMAGE;
            return this;
        }

        /**
         * Sets file list content and automatically sets type to FILE_LIST.
         * Makes a defensive copy of the list.
         *
         * @param files file list content
         * @return this builder
         * @throws NullPointerException if files is null
         */
        public Builder fileListData(List<File> files) {
            Objects.requireNonNull(files, "files cannot be null");
            this.data = List.copyOf(files);
            this.type = ContentType.FILE_LIST;
            return this;
        }

        /**
         * Sets the content type to UNKNOWN (for unrecognized clipboard content).
         *
         * @return this builder
         */
        public Builder unknownType() {
            this.type = ContentType.UNKNOWN;
            this.data = null;
            return this;
        }

        /**
         * Sets the SHA-256 hash for content identification.
         *
         * @param hash content hash
         * @return this builder
         * @throws NullPointerException if hash is null
         */
        public Builder hash(String hash) {
            this.hash = Objects.requireNonNull(hash, "hash cannot be null");
            return this;
        }

        /**
         * Sets the content size in bytes.
         *
         * @param size size in bytes
         * @return this builder
         * @throws IllegalArgumentException if size is negative
         */
        public Builder size(long size) {
            if (size < 0) {
                throw new IllegalArgumentException("size cannot be negative");
            }
            this.size = size;
            return this;
        }

        /**
         * Sets the timestamp when content was captured.
         * If not set, defaults to current time at build.
         *
         * @param timestamp capture timestamp
         * @return this builder
         */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Sets the available data flavors from the clipboard.
         * Makes a defensive copy of the list.
         *
         * @param flavors data flavors
         * @return this builder
         */
        public Builder flavors(List<DataFlavor> flavors) {
            this.flavors = flavors != null ? new ArrayList<>(flavors) : null;
            return this;
        }

        /**
         * Sets raw bytes with defensive copy.
         *
         * @param rawBytes raw byte content
         * @return this builder
         */
        public Builder rawBytes(byte[] rawBytes) {
            this.rawBytes = rawBytes != null ? rawBytes.clone() : null;
            return this;
        }

        /**
         * Builds the ClipboardContent instance.
         *
         * @return new ClipboardContent instance
         * @throws NullPointerException  if type or hash is not set
         * @throws IllegalStateException if type and data are inconsistent
         */
        public ClipboardContent build() {
            Objects.requireNonNull(type, "type must be set (use textData, imageData, fileListData, or unknownType)");
            Objects.requireNonNull(hash, "hash must be set");

            validateTypeDataConsistency();

            return new ClipboardContent(this);
        }

        private void validateTypeDataConsistency() {
            switch (type) {
                case TEXT -> {
                    if (!(data instanceof String)) {
                        throw new IllegalStateException("TEXT type requires String data");
                    }
                }
                case IMAGE -> {
                    if (!(data instanceof BufferedImage)) {
                        throw new IllegalStateException("IMAGE type requires BufferedImage data");
                    }
                }
                case FILE_LIST -> {
                    if (!(data instanceof List)) {
                        throw new IllegalStateException("FILE_LIST type requires List<File> data");
                    }
                }
                case UNKNOWN -> {
                    // data can be null for UNKNOWN type
                }
            }
        }
    }
}
