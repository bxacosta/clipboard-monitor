package dev.bxlab.clipboard.monitor.util;

import dev.bxlab.clipboard.monitor.exception.ClipboardException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;

/**
 * Utility class for image conversion operations.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ImageUtils {

    private static final Component DUMMY_COMPONENT = new Component() {
    };
    private static final int IMAGE_LOAD_TIMEOUT_MS = 5000;

    /**
     * Converts an Image to BufferedImage.
     * If the image is already a BufferedImage, returns it directly.
     *
     * @param img image to convert
     * @return BufferedImage representation
     * @throws ClipboardException if image dimensions cannot be determined
     */
    public static BufferedImage toBufferedImage(Image img) {
        if (img == null) {
            throw new IllegalArgumentException("Image cannot be null");
        }

        if (img instanceof BufferedImage buffered) {
            return buffered;
        }

        int width = img.getWidth(null);
        int height = img.getHeight(null);

        if (width <= 0 || height <= 0) {
            waitForImageLoad(img);
            width = img.getWidth(null);
            height = img.getHeight(null);

            if (width <= 0 || height <= 0) {
                throw new ClipboardException("Image dimensions not available");
            }
        }

        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buffered.createGraphics();
        try {
            g.drawImage(img, 0, 0, null);
        } finally {
            g.dispose();
        }

        return buffered;
    }

    private static void waitForImageLoad(Image img) {
        MediaTracker tracker = new MediaTracker(DUMMY_COMPONENT);
        tracker.addImage(img, 0);
        try {
            tracker.waitForID(0, IMAGE_LOAD_TIMEOUT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
