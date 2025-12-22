# Basic Examples

Simple, single-purpose examples demonstrating individual features of the Clipboard Monitor library.

## Available Examples

### BasicReadExample

Reads and displays the current clipboard content.

```bash
./gradlew -q --console=plain :examples:basic:runBasicRead
```

### BasicWriteExample

Writes text to the clipboard and verifies the operation.

```bash
./gradlew -q --console=plain :examples:basic:runBasicWrite
```

### ImageExample

Monitors the clipboard for image content. Displays dimensions and size when an image is detected.

```bash
./gradlew -q --console=plain :examples:basic:runImageExample
```

### FileListExample

Monitors the clipboard for file list content. Displays file names and sizes when files are copied.

```bash
./gradlew -q --console=plain :examples:basic:runFileListExample
```

### ListenerExample

Demonstrates the use of multiple listeners attached to a single monitor instance.

```bash
./gradlew -q --console=plain :examples:basic:runListenerExample
```

## List Available Tasks

```bash
./gradlew :examples:basic:tasks --group=examples
```
