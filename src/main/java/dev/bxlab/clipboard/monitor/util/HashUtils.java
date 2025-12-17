package dev.bxlab.clipboard.monitor.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Utility class for calculating SHA-256 hashes of various content types.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HashUtils {

    private static final HexFormat HEX = HexFormat.of();
    private static final String ALGORITHM = "SHA-256";

    /**
     * Calculates SHA-256 hash of a byte array.
     *
     * @param data bytes to hash
     * @return hexadecimal hash string
     */
    public static String sha256(byte[] data) {
        if (data == null) {
            data = new byte[0];
        }
        return HEX.formatHex(getDigest().digest(data));
    }

    /**
     * Calculates SHA-256 hash of a string using UTF-8 encoding.
     *
     * @param text text to hash
     * @return hexadecimal hash string
     */
    public static String sha256(String text) {
        if (text == null) {
            return sha256(new byte[0]);
        }
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Calculates SHA-256 hash of an image based on dimensions and sampled pixels.
     *
     * @param image image to hash
     * @return hexadecimal hash string
     */
    public static String hashImage(BufferedImage image) {
        if (image == null) {
            return sha256(new byte[0]);
        }

        MessageDigest digest = getDigest();

        digest.update(intToBytes(image.getWidth()));
        digest.update(intToBytes(image.getHeight()));
        digest.update(intToBytes(image.getType()));

        int totalPixels = image.getWidth() * image.getHeight();
        int sampleSize = Math.min(1000, totalPixels);
        int step = Math.max(1, totalPixels / sampleSize);

        for (int i = 0; i < totalPixels; i += step) {
            int x = i % image.getWidth();
            int y = i / image.getWidth();
            digest.update(intToBytes(image.getRGB(x, y)));
        }

        return HEX.formatHex(digest.digest());
    }

    /**
     * Calculates SHA-256 hash of a file list based on paths, modification times, and sizes.
     *
     * @param files files to hash
     * @return hexadecimal hash string
     */
    public static String hashFileList(List<File> files) {
        if (files == null || files.isEmpty()) {
            return sha256(new byte[0]);
        }

        StringBuilder sb = new StringBuilder();
        for (File file : files) {
            if (file != null) {
                sb.append(file.getAbsolutePath())
                        .append("|")
                        .append(file.lastModified())
                        .append("|")
                        .append(file.length())
                        .append("\n");
            }
        }
        return sha256(sb.toString());
    }

    private static MessageDigest getDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all JVM implementations
            throw new AssertionError("SHA-256 algorithm not available", e);
        }
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value >> 24),
                (byte) (value >> 16),
                (byte) (value >> 8),
                (byte) value
        };
    }
}
