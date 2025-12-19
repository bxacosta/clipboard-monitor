package dev.bxlab.clipboard.monitor.internal;

import dev.bxlab.clipboard.monitor.exception.ClipboardUnavailableException;
import dev.bxlab.clipboard.monitor.util.HashUtils;
import dev.bxlab.clipboard.monitor.util.ImageUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Provides static access to the system clipboard with automatic retry logic.
 * <p>
 * Handles clipboard busy states and provides methods for reading different
 * content types (text, image, files).
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClipboardAccessor {

    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 50;

    /**
     * Gets the system clipboard.
     *
     * @return system clipboard
     * @throws ClipboardUnavailableException if running in headless mode
     */
    public static Clipboard getClipboard() {
        try {
            return Toolkit.getDefaultToolkit().getSystemClipboard();
        } catch (HeadlessException e) {
            throw new ClipboardUnavailableException(
                    "Clipboard not available in headless environment. " +
                            "This library requires a graphical environment.", e);
        }
    }

    /**
     * Gets the current clipboard contents with automatic retry on busy.
     *
     * @return current transferable or null if the clipboard is empty
     * @throws ClipboardUnavailableException if the clipboard cannot be accessed after retries
     */
    public static Transferable getContents() {
        return getContentsWithRetry(getClipboard());
    }

    /**
     * Reads text from the clipboard if available.
     *
     * @return text content or null if not text
     */
    public static String readText() {
        Transferable transferable = getContents();
        if (transferable == null) {
            return null;
        }

        try {
            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return (String) transferable.getTransferData(DataFlavor.stringFlavor);
            }
        } catch (UnsupportedFlavorException | IOException e) {
            log.debug("Could not read text from clipboard: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Reads image from the clipboard if available.
     *
     * @return image content or null if not image
     */
    public static BufferedImage readImage() {
        Transferable transferable = getContents();
        if (transferable == null) {
            return null;
        }

        try {
            if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) transferable.getTransferData(DataFlavor.imageFlavor);
                return ImageUtils.toBufferedImage(img);
            }
        } catch (UnsupportedFlavorException | IOException e) {
            log.debug("Could not read image from clipboard: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Reads a file list from the clipboard if available.
     *
     * @return file list or null if not a file list
     */
    @SuppressWarnings("unchecked")
    public static List<File> readFiles() {
        Transferable transferable = getContents();
        if (transferable == null) {
            return List.of();
        }

        try {
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                return (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
            }
        } catch (UnsupportedFlavorException | IOException e) {
            log.debug("Could not read files from clipboard: {}", e.getMessage());
        }

        return List.of();
    }

    /**
     * Calculates hash of a transferable.
     *
     * @param transferable content to hash
     * @return hash string
     */
    public static String calculateHash(Transferable transferable) {
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
                return HashUtils.sha256(buffered);
            }

            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                return HashUtils.sha256(files);
            }

            // Unknown type - hash the flavor descriptions
            return HashUtils.sha256(Arrays.toString(transferable.getTransferDataFlavors()));

        } catch (Exception e) {
            log.warn("Error calculating hash, using timestamp fallback", e);
            return HashUtils.sha256(String.valueOf(System.nanoTime()));
        }
    }

    private static Transferable getContentsWithRetry(Clipboard clipboard) {
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
}
