package dev.bxlab.clipboard.monitor.transferable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Transferable implementation for file lists.
 * Used to write file references to the system clipboard.
 */
public final class FileListTransferable implements Transferable {

    private final List<File> files;

    /**
     * Creates a new file list transferable.
     *
     * @param files files to transfer
     * @throws NullPointerException     if files is null
     * @throws IllegalArgumentException if files contains null elements
     */
    public FileListTransferable(List<File> files) {
        Objects.requireNonNull(files, "files cannot be null");
        if (files.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("files cannot contain null elements");
        }
        this.files = List.copyOf(files);
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{DataFlavor.javaFileListFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.javaFileListFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return files;
    }
}
