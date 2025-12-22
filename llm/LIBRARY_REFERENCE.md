# Clipboard Monitor API Reference

**Java 21** | `dev.bxlab.clipboard.monitor` | **v2.0.0-SNAPSHOT**

System clipboard monitoring library. Supports text, images, and file lists.

## Installation

```gradle
dependencies {
    implementation 'dev.bxlab.clipboard:clipboard-monitor:2.0.0-SNAPSHOT'
    runtimeOnly 'ch.qos.logback:logback-classic:1.5.22'  // SLF4J implementation required
}
```

## Quick Start

```java
try (ClipboardMonitor monitor = ClipboardMonitor.builder()
        .detector(PollingDetector.defaults())
        .listener(content -> System.out.println("Changed: " + content.type()))
        .build()) {
    monitor.start();
    // ... application runs
}
```

---

## ClipboardMonitor

Main entry point. Implements `AutoCloseable`. Must call `start()` to begin monitoring.

### Static Methods

| Method      | Returns | Description              |
|-------------|---------|--------------------------|
| `builder()` | Builder | Creates builder instance |

### Instance Methods

| Method                 | Parameters | Returns                      | Description                                       |
|------------------------|------------|------------------------------|---------------------------------------------------|
| `start()`              | -          | void                         | Starts monitoring (idempotent)                    |
| `close()`              | -          | void                         | Stops monitoring, releases resources (idempotent) |
| `isRunning()`          | -          | boolean                      | Returns true if monitoring is active              |
| `write(String)`        | text       | void                         | Writes text to clipboard                          |
| `write(BufferedImage)` | image      | void                         | Writes image to clipboard                         |
| `write(List<File>)`    | files      | void                         | Writes files to clipboard                         |
| `read()`               | -          | ClipboardContent             | Reads clipboard (throws on error)                 |
| `tryRead()`            | -          | Optional\<ClipboardContent\> | Reads clipboard (empty on error)                  |

**Note**: `write()` methods track content internally to prevent notification loops. Content written via `write()` won't trigger listeners.

### Builder

| Method                        | Parameters | Required | Default | Description                                  |
|-------------------------------|------------|----------|---------|----------------------------------------------|
| `detector(ChangeDetector)`    | detector   | **Yes**  | -       | Sets change detection strategy               |
| `listener(ClipboardListener)` | listener   | **Yes**  | -       | Adds listener (can be called multiple times) |
| `debounce(Duration)`          | duration   | No       | 50ms    | Debounce delay for grouping rapid changes    |
| `notifyOnStart(boolean)`      | notify     | No       | false   | Notify initial clipboard content on start    |

**Validation**: `build()` throws `IllegalStateException` if detector or listener is missing. Throws `IllegalArgumentException` if debounce is negative.

---

## Detectors

Change detection strategies. Must provide one via `detector()`.

| Detector            | Default Config | Description                  | Use Case                         |
|---------------------|----------------|------------------------------|----------------------------------|
| `PollingDetector`   | 200ms interval | Polls clipboard periodically | Reliable, works on all platforms |
| `OwnershipDetector` | 50ms delay     | Uses ownership loss events   | Lower latency, less reliable     |

### PollingDetector

```java
// Defaults (200ms)
.detector(PollingDetector.defaults())

// Custom interval
.detector(PollingDetector.builder()
    .interval(Duration.ofMillis(100))
    .build())
```

**Builder Methods**:
- `interval(Duration)` - Polling interval (must be positive, default: 200ms)
- `build()` - Creates detector

**Public Methods**:
- `updateLastHash(String)` - Updates last known hash after write

### OwnershipDetector

```java
// Defaults (50ms delay)
.detector(OwnershipDetector.defaults())

// Custom delay
.detector(OwnershipDetector.builder()
    .delay(Duration.ofMillis(100))
    .build())
```

**Builder Methods**:
- `delay(Duration)` - Delay before reading after ownership loss (default: 50ms)
- `build()` - Creates detector

**Public Methods**:
- `retakeOwnership(Transferable)` - Retakes clipboard ownership after write

---

## ClipboardContent

Sealed interface representing clipboard content at a moment in time. Immutable and thread-safe.

**Implementations**: `TextContent`, `ImageContent`, `FilesContent`, `UnknownContent`

### Common Methods

| Method        | Returns                   | Description                        |
|---------------|---------------------------|------------------------------------|
| `type()`      | ContentType               | TEXT, IMAGE, FILES, or UNKNOWN     |
| `hash()`      | String                    | SHA-256 hash for change detection  |
| `timestamp()` | Instant                   | When content was captured          |
| `size()`      | long                      | Content size in bytes              |
| `asText()`    | Optional\<String\>        | Text content (empty if not TEXT)   |
| `asImage()`   | Optional\<BufferedImage\> | Image content (empty if not IMAGE) |
| `asFiles()`   | Optional\<List\<File\>\>  | File list (empty if not FILES)     |

### TextContent

Represents text clipboard content.

**Constructor**: `TextContent(String text, String hash, Instant timestamp, long sizeBytes)`

| Method        | Returns     | Description         |
|---------------|-------------|---------------------|
| `text()`      | String      | The text content    |
| `hash()`      | String      | SHA-256 hash        |
| `timestamp()` | Instant     | Capture timestamp   |
| `sizeBytes()` | long        | UTF-8 encoded size  |
| `type()`      | ContentType | Returns TEXT        |
| `size()`      | long        | Same as sizeBytes() |

### ImageContent

Represents image clipboard content.

**Constructors**:
- `ImageContent(BufferedImage image, String hash, Instant timestamp, int width, int height)`
- `ImageContent.of(BufferedImage image, String hash, Instant timestamp)` - Extracts dimensions

| Method        | Returns       | Description                     |
|---------------|---------------|---------------------------------|
| `image()`     | BufferedImage | The image content               |
| `width()`     | int           | Image width in pixels           |
| `height()`    | int           | Image height in pixels          |
| `hash()`      | String        | SHA-256 hash                    |
| `timestamp()` | Instant       | Capture timestamp               |
| `type()`      | ContentType   | Returns IMAGE                   |
| `size()`      | long          | width × height × 4 (ARGB bytes) |

### FilesContent

Represents file list clipboard content.

**Constructors**:
- `FilesContent(List<File> files, String hash, Instant timestamp, long totalSize)`
- `FilesContent.of(List<File> files, String hash, Instant timestamp)` - Calculates size

| Method        | Returns      | Description             |
|---------------|--------------|-------------------------|
| `files()`     | List\<File\> | Immutable file list     |
| `totalSize()` | long         | Total size of all files |
| `hash()`      | String       | SHA-256 hash            |
| `timestamp()` | Instant      | Capture timestamp       |
| `type()`      | ContentType  | Returns FILES           |
| `size()`      | long         | Same as totalSize()     |

### UnknownContent

Represents unknown/unsupported clipboard content.

**Constructor**: `UnknownContent(String hash, Instant timestamp)`

| Method        | Returns     | Description       |
|---------------|-------------|-------------------|
| `hash()`      | String      | SHA-256 hash      |
| `timestamp()` | Instant     | Capture timestamp |
| `type()`      | ContentType | Returns UNKNOWN   |
| `size()`      | long        | Always returns 0  |

---

## ClipboardListener

Functional interface for receiving clipboard change notifications.

```java
@FunctionalInterface
public interface ClipboardListener {
    void onClipboardChange(ClipboardContent content);
    default void onError(Exception error) { }
}
```

**Thread Safety**: Callbacks invoked on virtual threads. Must be thread-safe if accessing shared state.

**Usage**:
```java
// Lambda
.listener(content -> System.out.println(content.type()))

// With error handling
.listener(new ClipboardListener() {
    @Override
    public void onClipboardChange(ClipboardContent content) {
        processContent(content);
    }
    
    @Override
    public void onError(Exception error) {
        log.error("Clipboard error", error);
    }
})
```

---

## ContentType

Enum for content classification.

| Value     | Description                     | Access Method |
|-----------|---------------------------------|---------------|
| `TEXT`    | Plain text, HTML, RTF           | `asText()`    |
| `IMAGE`   | PNG, JPEG, BMP as BufferedImage | `asImage()`   |
| `FILES`   | List of files                   | `asFiles()`   |
| `UNKNOWN` | Unsupported type                | none          |

---

## Usage Examples

### Basic Monitoring

```java
try (ClipboardMonitor monitor = ClipboardMonitor.builder()
        .detector(PollingDetector.defaults())
        .listener(content -> System.out.println("Type: " + content.type()))
        .build()) {
    monitor.start();
    Thread.sleep(60000); // monitor for 60s
}
```

### Process by Type

```java
.listener(content -> {
    switch (content.type()) {
        case TEXT -> content.asText().ifPresent(text -> 
            System.out.println("Text: " + text));
        case IMAGE -> content.asImage().ifPresent(img -> 
            System.out.println("Image: " + img.getWidth() + "x" + img.getHeight()));
        case FILES -> content.asFiles().ifPresent(files -> 
            files.forEach(f -> System.out.println("File: " + f.getName())));
        case UNKNOWN -> System.out.println("Unknown content");
    }
})
```

### Write to Clipboard

```java
// Text
monitor.write("Hello World");

// Image
BufferedImage image = ImageIO.read(new File("image.png"));
monitor.write(image);

// Files
monitor.write(List.of(new File("doc.pdf"), new File("data.csv")));
```

### Read without Monitoring

```java
// Safe read (returns Optional)
monitor.tryRead().ifPresent(content -> {
    System.out.println("Current: " + content.type());
});

// Direct read (throws on error)
try {
    ClipboardContent content = monitor.read();
    System.out.println("Type: " + content.type());
} catch (ClipboardUnavailableException e) {
    System.err.println("Clipboard locked");
}
```

### Bidirectional Sync

```java
// Content written via write() won't trigger listener (prevents loops)
ClipboardMonitor monitor = ClipboardMonitor.builder()
    .detector(PollingDetector.defaults())
    .listener(content -> sendToRemote(content))
    .build();

monitor.start();

// Receive from remote - won't trigger listener
String remoteText = receiveFromRemote();
monitor.write(remoteText);
```

### Custom Configuration

```java
ClipboardMonitor monitor = ClipboardMonitor.builder()
    .detector(PollingDetector.builder()
        .interval(Duration.ofMillis(100))  // faster polling
        .build())
    .listener(this::handleChange)
    .debounce(Duration.ofMillis(30))       // shorter debounce
    .notifyOnStart(true)                    // get initial content
    .build();
```

### Multiple Listeners

```java
ClipboardMonitor monitor = ClipboardMonitor.builder()
    .detector(PollingDetector.defaults())
    .listener(content -> logChange(content))
    .listener(content -> syncToCloud(content))
    .listener(content -> updateUI(content))
    .build();
```

**Note**: Each listener runs in its own virtual thread. A slow or failing listener doesn't affect others.

### Access Type-Specific Properties

```java
monitor.tryRead().ifPresent(content -> {
    if (content instanceof TextContent text) {
        System.out.println("Text length: " + text.sizeBytes());
    } else if (content instanceof ImageContent image) {
        System.out.println("Dimensions: " + image.width() + "x" + image.height());
    } else if (content instanceof FilesContent files) {
        System.out.println("File count: " + files.files().size());
        System.out.println("Total size: " + files.totalSize());
    }
});
```

---

## Exceptions

All exceptions extend `ClipboardException` (RuntimeException).

| Exception                       | When Thrown                             |
|---------------------------------|-----------------------------------------|
| `ClipboardException`            | Base exception for clipboard errors     |
| `ClipboardUnavailableException` | Clipboard locked by another application |
| `ClipboardChangedException`     | Content changed during read (transient) |

---

## Thread Safety

- All public methods are thread-safe
- Listeners invoked on virtual threads (one per listener)
- `ClipboardContent` implementations are immutable records
- Safe to share content between threads

---

## Resource Management

- `ClipboardMonitor` implements `AutoCloseable`
- Always use try-with-resources or call `close()` explicitly
- `close()` is idempotent (safe to call multiple times)
- Virtual threads used internally (lightweight, no thread pool needed)

---

## Logging

Library uses SLF4J. Requires runtime implementation (e.g., `logback-classic`).

**Log Levels**:
- **DEBUG**: Internal flow, state changes, hash values
- **INFO**: Lifecycle events (start, stop, configuration)
- **WARN**: Recoverable issues, fallback behavior
- **ERROR**: Failures with exception details

**Configuration**: Set logger `dev.bxlab.clipboard.monitor` to desired level.

---

## Required Imports

```java
import dev.bxlab.clipboard.monitor.ClipboardMonitor;
import dev.bxlab.clipboard.monitor.ClipboardContent;
import dev.bxlab.clipboard.monitor.ClipboardListener;
import dev.bxlab.clipboard.monitor.ContentType;
import dev.bxlab.clipboard.monitor.TextContent;
import dev.bxlab.clipboard.monitor.ImageContent;
import dev.bxlab.clipboard.monitor.FilesContent;
import dev.bxlab.clipboard.monitor.UnknownContent;
import dev.bxlab.clipboard.monitor.detector.PollingDetector;
import dev.bxlab.clipboard.monitor.detector.OwnershipDetector;
import dev.bxlab.clipboard.monitor.exception.ClipboardException;
import dev.bxlab.clipboard.monitor.exception.ClipboardUnavailableException;
```
