package dev.bxlab.clipboard.examples.interactive;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Utility class for formatting and loading content in the demo.
 */
public final class ContentHelper {

    private ContentHelper() {
        // Utility class
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg");
    private static final int DEFAULT_MAX_LINES = 500;

    /**
     * Formats byte size to human-readable string.
     *
     * @param bytes size in bytes
     * @return formatted string like "1.2 MB"
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Formats duration to human-readable string.
     *
     * @param duration duration to format
     * @return formatted string like "2m 15s"
     */
    public static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return String.format("%dm %ds", seconds / 60, seconds % 60);
        }
        return String.format("%dh %dm %ds", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    /**
     * Returns the current timestamp formatted for display.
     *
     * @return formatted timestamp like "12:30:45"
     */
    public static String formatTimestamp() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }

    /**
     * Truncates text preserving line breaks, limiting to a maximum number of lines.
     *
     * @param text     text to truncate
     * @param maxLines maximum number of lines
     * @return truncated text with original formatting
     */
    public static String truncateLines(String text, int maxLines) {
        if (text == null) {
            return "";
        }

        String[] lines = text.split("\r?\n", maxLines + 1);
        if (lines.length <= maxLines) {
            return text;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(lines[i]);
        }
        sb.append("\n... (truncated, ").append(lines.length - maxLines).append("+ more lines)");
        return sb.toString();
    }

    /**
     * Truncates text preserving line breaks, using the default maximum lines.
     *
     * @param text text to truncate
     * @return truncated text with original formatting
     */
    public static String truncateLines(String text) {
        return truncateLines(text, DEFAULT_MAX_LINES);
    }

    /**
     * Loads an image from a file path.
     *
     * @param path absolute path to an image file
     * @return loaded BufferedImage
     * @throws IOException if the file cannot be read or is not a valid image
     */
    public static BufferedImage loadImage(String path) throws IOException {
        File file = new File(path);
        validateFile(file);

        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Not a valid image file: " + path);
        }
        return image;
    }

    /**
     * Parses file paths from user input.
     * Supports multiple paths separated by semicolon.
     *
     * @param input user input string
     * @return list of valid files
     * @throws IOException if any file is invalid
     */
    public static List<File> parseFilePaths(String input) throws IOException {
        if (input == null || input.isBlank()) {
            throw new IOException("No file path provided");
        }

        List<File> files = new ArrayList<>();
        String[] paths = input.split(";");

        for (String path : paths) {
            String trimmed = path.trim();
            if (!trimmed.isEmpty()) {
                File file = new File(trimmed);
                validateFile(file);
                files.add(file);
            }
        }

        if (files.isEmpty()) {
            throw new IOException("No valid file paths provided");
        }

        return files;
    }

    /**
     * Checks if a file path points to an image file based on an extension.
     *
     * @param path file path to check
     * @return true if the file has an image extension
     */
    public static boolean isImageFile(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase();
        return IMAGE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static void validateFile(File file) throws IOException {
        if (!file.exists()) {
            throw new IOException("File not found: " + file.getAbsolutePath());
        }
        if (!file.isFile()) {
            throw new IOException("Not a file: " + file.getAbsolutePath());
        }
        if (!file.canRead()) {
            throw new IOException("Cannot read file: " + file.getAbsolutePath());
        }
    }
}
