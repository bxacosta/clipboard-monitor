package dev.bxlab.clipboard.monitor.internal;

import dev.bxlab.clipboard.monitor.ClipboardContent;
import dev.bxlab.clipboard.monitor.exception.ClipboardChangedException;
import dev.bxlab.clipboard.monitor.exception.ClipboardException;
import dev.bxlab.clipboard.monitor.exception.ClipboardUnavailableException;
import dev.bxlab.clipboard.monitor.util.HashUtils;
import dev.bxlab.clipboard.monitor.util.ImageUtils;
import lombok.extern.slf4j.Slf4j;

import java.awt.Image;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Reads clipboard content and converts it to ClipboardContent.
 * Supports text, images, and file lists. Includes automatic retries when clipboard is busy.
 */
@Slf4j
public final class ContentReader {

    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 50;

    private final Clipboard clipboard;

    /**
     * Creates a content reader with the specified clipboard.
     *
     * @param clipboard clipboard to read from
     */
    public ContentReader(Clipboard clipboard) {
        this.clipboard = clipboard;
    }

    /**
     * Reads current clipboard content.
     *
     * @return clipboard content
     * @throws ClipboardException if clipboard cannot be read
     */
    public ClipboardContent read() {
        return read(null);
    }

    /**
     * Reads current clipboard content with a pre-calculated hash.
     *
     * @param precomputedHash optional pre-calculated hash (if null, hash will be calculated)
     * @return clipboard content
     * @throws ClipboardException if clipboard cannot be read
     */
    public ClipboardContent read(String precomputedHash) {
        Transferable transferable = getContentsWithRetry();

        if (transferable == null) {
            return createEmptyContent();
        }

        DataFlavor[] flavors = transferable.getTransferDataFlavors();
        List<DataFlavor> flavorList = Arrays.asList(flavors);

        try {
            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return readText(transferable, flavorList, precomputedHash);
            }

            if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                return readImage(transferable, flavorList, precomputedHash);
            }

            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                return readFileList(transferable, flavorList, precomputedHash);
            }

            return createUnknownContent(flavorList);

        } catch (UnsupportedFlavorException e) {
            throw new ClipboardChangedException("Clipboard changed during read", e);
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
            return HashUtils.sha256(new byte[0]);
        }

        try {
            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = (String) transferable.getTransferData(DataFlavor.stringFlavor);
                return HashUtils.sha256(text);
            }

            if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) transferable.getTransferData(DataFlavor.imageFlavor);
                BufferedImage buffered = ImageUtils.toBufferedImage(img);
                return HashUtils.hashImage(buffered);
            }

            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                return HashUtils.hashFileList(files);
            }

            return HashUtils.sha256(Arrays.toString(transferable.getTransferDataFlavors()));

        } catch (Exception e) {
            log.warn("Error calculating hash, using timestamp fallback", e);
            return HashUtils.sha256(String.valueOf(System.nanoTime()));
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
                        Thread.sleep(INITIAL_RETRY_DELAY_MS * (i + 1L));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ClipboardUnavailableException(
                                "Interrupted while waiting for clipboard", ie);
                    }
                }
            }
        }

        throw new ClipboardUnavailableException(
                "Clipboard unavailable after " + MAX_RETRIES + " retries", lastException);
    }

    private ClipboardContent readText(Transferable transferable, List<DataFlavor> flavors, String precomputedHash)
            throws UnsupportedFlavorException, IOException {

        String text = (String) transferable.getTransferData(DataFlavor.stringFlavor);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String hash = Optional.ofNullable(precomputedHash).orElse(HashUtils.sha256(bytes));

        return ClipboardContent.builder()
                .textData(text)
                .hash(hash)
                .size(bytes.length)
                .timestamp(Instant.now())
                .flavors(flavors)
                .rawBytes(bytes)
                .build();
    }

    private ClipboardContent readImage(Transferable transferable, List<DataFlavor> flavors, String precomputedHash)
            throws UnsupportedFlavorException, IOException {

        Image img = (Image) transferable.getTransferData(DataFlavor.imageFlavor);
        BufferedImage buffered = ImageUtils.toBufferedImage(img);

        long estimatedSize = (long) buffered.getWidth() * buffered.getHeight() * 4;
        String hash = Optional.ofNullable(precomputedHash).orElse(HashUtils.hashImage(buffered));

        return ClipboardContent.builder()
                .imageData(buffered)
                .hash(hash)
                .size(estimatedSize)
                .timestamp(Instant.now())
                .flavors(flavors)
                .build();
    }

    @SuppressWarnings("unchecked")
    private ClipboardContent readFileList(Transferable transferable, List<DataFlavor> flavors, String precomputedHash)
            throws UnsupportedFlavorException, IOException {

        List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);

        long totalSize = 0;
        StringBuilder pathBuilder = new StringBuilder();

        for (File file : files) {
            if (file.exists() && file.isFile()) {
                totalSize += file.length();
            }
            pathBuilder.append(file.getAbsolutePath()).append("\n");
        }

        String hash = Optional.ofNullable(precomputedHash).orElse(HashUtils.hashFileList(files));
        byte[] pathBytes = pathBuilder.toString().getBytes(StandardCharsets.UTF_8);

        return ClipboardContent.builder()
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
                .unknownType()
                .hash(HashUtils.sha256(new byte[0]))
                .size(0)
                .timestamp(Instant.now())
                .build();
    }

    private ClipboardContent createUnknownContent(List<DataFlavor> flavors) {
        String flavorInfo = flavors.toString();
        byte[] bytes = flavorInfo.getBytes(StandardCharsets.UTF_8);

        return ClipboardContent.builder()
                .unknownType()
                .hash(HashUtils.sha256(bytes))
                .size(0)
                .timestamp(Instant.now())
                .flavors(flavors)
                .rawBytes(bytes)
                .build();
    }
}
