package dev.bxlab.clipboard.monitor.transferable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Transferable implementation for images.
 * Used to write images to the system clipboard.
 */
public final class ImageTransferable implements Transferable {

    private final BufferedImage image;

    /**
     * Creates a new image transferable.
     *
     * @param image image to transfer
     * @throws NullPointerException if image is null
     */
    public ImageTransferable(BufferedImage image) {
        this.image = Objects.requireNonNull(image, "image cannot be null");
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{DataFlavor.imageFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.imageFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return image;
    }
}
