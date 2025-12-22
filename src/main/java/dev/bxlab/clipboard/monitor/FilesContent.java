package dev.bxlab.clipboard.monitor;

import dev.bxlab.clipboard.monitor.util.TextUtils;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A list of files from the clipboard.
 *
 * <pre>{@code
 * FilesContent content = new FilesContent(files, hash, Instant.now(), totalSize);
 * System.out.println("Files: " + content.files().size());
 * content.files().forEach(f -> System.out.println("  - " + f.getName()));
 * }</pre>
 *
 * @param files     the list of files (never null, immutable copy)
 * @param hash      SHA-256 hash of the content
 * @param timestamp when the content was captured
 * @param totalSize the total size of all files in bytes
 */
public record FilesContent(
        List<File> files,
        String hash,
        Instant timestamp,
        long totalSize
) implements ClipboardContent {

    /**
     * Creates a new FilesContent instance.
     *
     * @throws NullPointerException     if files, hash, or timestamp is null
     * @throws IllegalArgumentException if totalSize is negative
     */
    public FilesContent {
        Objects.requireNonNull(files, "files cannot be null");
        Objects.requireNonNull(hash, "hash cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        if (totalSize < 0) {
            throw new IllegalArgumentException("totalSize cannot be negative: " + totalSize);
        }
        files = List.copyOf(files);
    }

    /**
     * Creates a new FilesContent instance, calculating the total size from files.
     *
     * @param files     the list of files
     * @param hash      SHA-256 hash of the content
     * @param timestamp when the content was captured
     * @return new FilesContent instance
     * @throws NullPointerException if any parameter is null
     */
    public static FilesContent of(List<File> files, String hash, Instant timestamp) {
        Objects.requireNonNull(files, "files cannot be null");
        long totalSize = files.stream()
                .filter(Objects::nonNull)
                .filter(File::exists)
                .mapToLong(File::length)
                .sum();
        return new FilesContent(files, hash, timestamp, totalSize);
    }

    @Override
    public ContentType type() {
        return ContentType.FILES;
    }

    @Override
    public long size() {
        return totalSize;
    }

    @Override
    public String toString() {
        return "FilesContent{" +
                "fileCount=" + files.size() +
                ", hash='" + TextUtils.truncate(hash) + "'" +
                ", timestamp=" + timestamp +
                ", totalSize=" + totalSize +
                '}';
    }
}
