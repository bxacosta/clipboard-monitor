package dev.bxlab.clipboard.monitor.internal;

import dev.bxlab.clipboard.monitor.ClipboardContent;
import dev.bxlab.clipboard.monitor.ClipboardException;
import dev.bxlab.clipboard.monitor.ContentType;
import lombok.extern.slf4j.Slf4j;

import java.awt.Canvas;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Reads clipboard content and converts it to ClipboardContent.
 * Supports text, images, and file lists. Includes automatic retries when clipboard is busy.
 */
@Slf4j
public final class ContentReader {

    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 50;

    private final Clipboard clipboard;
    private final long maxContentSize;

    public ContentReader(long maxContentSize) {
        this.clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        this.maxContentSize = maxContentSize;
    }

    public ContentReader(Clipboard clipboard, long maxContentSize) {
        this.clipboard = clipboard;
        this.maxContentSize = maxContentSize;
    }

    /**
     * Reads current clipboard content.
     *
     * @return clipboard content
     * @throws ClipboardException if clipboard cannot be read
     */
    public ClipboardContent read() {
        Transferable transferable = getContentsWithRetry();

        if (transferable == null) {
            return createEmptyContent();
        }

        DataFlavor[] flavors = transferable.getTransferDataFlavors();
        List<DataFlavor> flavorList = Arrays.asList(flavors);

        try {
            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return readText(transferable, flavorList);
            }

            if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                return readImage(transferable, flavorList);
            }

            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                return readFileList(transferable, flavorList);
            }

            return createUnknownContent(flavorList);

        } catch (UnsupportedFlavorException e) {
            throw new ClipboardException.ClipboardChangedException(
                    "Clipboard changed during read", e);
        } catch (IOException e) {
            throw new ClipboardException("Error reading clipboard", e);
        }
    }

    /**
     * Calculates SHA-256 hash of clipboard content.
     *
     * @param transferable content to hash
     * @return SHA-256 hash in hexadecimal
     */
    public String calculateHash(Transferable transferable) {
        if (transferable == null) {
            return hashBytes(new byte[0]);
        }

        try {
            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = (String) transferable.getTransferData(DataFlavor.stringFlavor);
                return hashBytes(text.getBytes(StandardCharsets.UTF_8));
            }

            if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) transferable.getTransferData(DataFlavor.imageFlavor);
                return hashImage(img);
            }

            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                return hashFileList(files);
            }

            return hashBytes(Arrays.toString(transferable.getTransferDataFlavors())
                    .getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            log.warn("Error calculating hash, using timestamp fallback", e);
            return hashBytes(String.valueOf(System.nanoTime()).getBytes(StandardCharsets.UTF_8));
        }
    }

    private Transferable getContentsWithRetry() {
        IllegalStateException lastException = null;

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                return clipboard.getContents(null);
            } catch (IllegalStateException e) {
                lastException = e;
                log.debug("Clipboard busy, retry {} of {}", i + 1, MAX_RETRIES);

                if (i < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(INITIAL_RETRY_DELAY_MS * (i + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ClipboardException.ClipboardUnavailableException(
                                "Interrupted while waiting for clipboard", ie);
                    }
                }
            }
        }

        throw new ClipboardException.ClipboardUnavailableException(
                "Clipboard unavailable after " + MAX_RETRIES + " retries", lastException);
    }

    private ClipboardContent readText(Transferable transferable, List<DataFlavor> flavors)
            throws UnsupportedFlavorException, IOException {

        String text = (String) transferable.getTransferData(DataFlavor.stringFlavor);

        long estimatedSize = text.length() * 2L;
        if (estimatedSize > maxContentSize) {
            throw new ClipboardException.ContentTooLargeException(estimatedSize, maxContentSize);
        }

        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String hash = hashBytes(bytes);

        return ClipboardContent.builder()
                .type(ContentType.TEXT)
                .textData(text)
                .hash(hash)
                .size(bytes.length)
                .timestamp(Instant.now())
                .flavors(flavors)
                .rawBytes(bytes)
                .build();
    }

    private ClipboardContent readImage(Transferable transferable, List<DataFlavor> flavors)
            throws UnsupportedFlavorException, IOException {

        Image img = (Image) transferable.getTransferData(DataFlavor.imageFlavor);
        BufferedImage buffered = toBufferedImage(img);

        long estimatedSize = (long) buffered.getWidth() * buffered.getHeight() * 4;
        if (estimatedSize > maxContentSize) {
            throw new ClipboardException.ContentTooLargeException(estimatedSize, maxContentSize);
        }

        String hash = hashImage(buffered);

        return ClipboardContent.builder()
                .type(ContentType.IMAGE)
                .imageData(buffered)
                .hash(hash)
                .size(estimatedSize)
                .timestamp(Instant.now())
                .flavors(flavors)
                .build();
    }

    private ClipboardContent readFileList(Transferable transferable, List<DataFlavor> flavors)
            throws UnsupportedFlavorException, IOException {

        @SuppressWarnings("unchecked")
        List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);

        long totalSize = 0;
        StringBuilder pathBuilder = new StringBuilder();

        for (File file : files) {
            if (file.exists() && file.isFile()) {
                totalSize += file.length();
            }
            pathBuilder.append(file.getAbsolutePath()).append("\n");
        }

        if (totalSize > maxContentSize) {
            throw new ClipboardException.ContentTooLargeException(totalSize, maxContentSize);
        }

        String hash = hashFileList(files);
        byte[] pathBytes = pathBuilder.toString().getBytes(StandardCharsets.UTF_8);

        return ClipboardContent.builder()
                .type(ContentType.FILE_LIST)
                .fileListData(files)
                .hash(hash)
                .size(totalSize)
                .timestamp(Instant.now())
                .flavors(flavors)
                .rawBytes(pathBytes)
                .build();
    }

    private ClipboardContent createEmptyContent() {
        return ClipboardContent.builder()
                .type(ContentType.UNKNOWN)
                .hash(hashBytes(new byte[0]))
                .size(0)
                .timestamp(Instant.now())
                .build();
    }

    private ClipboardContent createUnknownContent(List<DataFlavor> flavors) {
        String flavorInfo = flavors.toString();
        byte[] bytes = flavorInfo.getBytes(StandardCharsets.UTF_8);

        return ClipboardContent.builder()
                .type(ContentType.UNKNOWN)
                .hash(hashBytes(bytes))
                .size(0)
                .timestamp(Instant.now())
                .flavors(flavors)
                .rawBytes(bytes)
                .build();
    }

    private BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage buffered) {
            return buffered;
        }

        int width = img.getWidth(null);
        int height = img.getHeight(null);

        if (width <= 0 || height <= 0) {
            MediaTracker tracker = new MediaTracker(new Canvas());
            tracker.addImage(img, 0);
            try {
                tracker.waitForID(0, 5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            width = img.getWidth(null);
            height = img.getHeight(null);

            if (width <= 0 || height <= 0) {
                throw new ClipboardException("Image dimensions not available");
            }
        }

        BufferedImage buffered = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g = buffered.createGraphics();
        try {
            g.drawImage(img, 0, 0, null);
        } finally {
            g.dispose();
        }

        return buffered;
    }

    private String hashBytes(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String hashImage(Image img) {
        try {
            BufferedImage buffered = (img instanceof BufferedImage b) ? b : toBufferedImage(img);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            digest.update(intToBytes(buffered.getWidth()));
            digest.update(intToBytes(buffered.getHeight()));
            digest.update(intToBytes(buffered.getType()));

            int sampleSize = Math.min(1000, buffered.getWidth() * buffered.getHeight());
            int step = Math.max(1, (buffered.getWidth() * buffered.getHeight()) / sampleSize);

            for (int i = 0; i < buffered.getWidth() * buffered.getHeight(); i += step) {
                int x = i % buffered.getWidth();
                int y = i / buffered.getWidth();
                digest.update(intToBytes(buffered.getRGB(x, y)));
            }

            return HexFormat.of().formatHex(digest.digest());

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String hashFileList(List<File> files) {
        StringBuilder sb = new StringBuilder();
        for (File file : files) {
            sb.append(file.getAbsolutePath())
                    .append("|")
                    .append(file.lastModified())
                    .append("|")
                    .append(file.length())
                    .append("\n");
        }
        return hashBytes(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value >> 24),
                (byte) (value >> 16),
                (byte) (value >> 8),
                (byte) value
        };
    }
}
