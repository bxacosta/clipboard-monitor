# Clipboard Monitor API Reference

**Java 21** | `dev.bxlab.clipboard.monitor` | **v1.0.0**

System clipboard monitoring library. Supports text, images, and file lists.

## Installation

Available through **GitLab Package Registry** (public access, no authentication required).

### Gradle

```gradle
repositories {
    mavenCentral()
    maven {
        name = "GitLab"
        url = uri("https://gitlab.com/api/v4/projects/70108742/packages/maven")
    }
}

dependencies {
    implementation 'dev.bxlab.clipboard:clipboard-monitor:1.0.0'
}
```

**Logging**: The library uses SLF4J for logging. You can optionally add an SLF4J implementation (e.g.,
`logback-classic`, `slf4j-simple`) if you need logging output.

### Maven

```xml

<repositories>
    <repository>
        <id>gitlab</id>
        <url>https://gitlab.com/api/v4/projects/70108742/packages/maven</url>
    </repository>
</repositories>

<dependencies>
<dependency>
    <groupId>dev.bxlab.clipboard</groupId>
    <artifactId>clipboard-monitor</artifactId>
    <version>1.0.0</version>
</dependency>
</dependencies>
```

**Logging**: The library uses SLF4J for logging. You can optionally add an SLF4J implementation (e.g.,
`logback-classic`, `slf4j-simple`) if you need logging output.

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

## ClipboardMonitor

Main entry point. Implements `AutoCloseable`. Must call `start()` to begin monitoring.

### Builder Configuration

```java
ClipboardMonitor.builder()
    .detector(ChangeDetector)       // Required: PollingDetector or OwnershipDetector
    .listener(ClipboardListener)    // Required: can be called multiple times
    .debounce(Duration)             // Optional: default 50ms
    .notifyOnStart(boolean)         // Optional: default false
    .build()
```

**Validation**: `build()` throws `IllegalStateException` if detector or listener missing, `IllegalArgumentException` if
debounce is negative.

### Instance Methods

| Method                 | Returns                      | Description                               |
|------------------------|------------------------------|-------------------------------------------|
| `start()`              | void                         | Starts monitoring (idempotent)            |
| `close()`              | void                         | Stops monitoring and releases resources   |
| `isRunning()`          | boolean                      | Returns true if monitoring is active      |
| `write(String)`        | void                         | Writes text to clipboard                  |
| `write(BufferedImage)` | void                         | Writes image to clipboard                 |
| `write(List<File>)`    | void                         | Writes file list to clipboard             |
| `read()`               | ClipboardContent             | Reads clipboard (throws on error)         |
| `tryRead()`            | Optional\<ClipboardContent\> | Reads clipboard (empty Optional on error) |

**Important**: Content written via `write()` won't trigger listeners (prevents notification loops).

## Detectors

Change detection strategies. Must provide one via `detector()`.

### PollingDetector

Polls clipboard periodically. Reliable, works on all platforms.

```java
// Default: 200ms interval
.detector(PollingDetector.defaults())

// Custom interval
.detector(PollingDetector.builder()
    .interval(Duration.ofMillis(100))
    .build())
```

**Configuration**: `interval(Duration)` - must be positive, default 200ms.

### OwnershipDetector

Uses clipboard ownership loss events. Lower latency but less reliable.

```java
// Default: 50ms delay
.detector(OwnershipDetector.defaults())

// Custom delay
.detector(OwnershipDetector.builder()
    .delay(Duration.ofMillis(100))
    .build())
```

**Configuration**: `delay(Duration)` - delay before reading after ownership loss, default 50ms.

## ClipboardContent

Sealed interface representing clipboard content. Immutable and thread-safe.

**Implementations**: `TextContent`, `ImageContent`, `FilesContent`, `UnknownContent`

### Common Methods

| Method        | Returns                   | Description                       |
|---------------|---------------------------|-----------------------------------|
| `type()`      | ContentType               | TEXT, IMAGE, FILES, or UNKNOWN    |
| `hash()`      | String                    | SHA-256 hash for change detection |
| `timestamp()` | Instant                   | When content was captured         |
| `size()`      | long                      | Content size in bytes             |
| `asText()`    | Optional\<String\>        | Text content if type is TEXT      |
| `asImage()`   | Optional\<BufferedImage\> | Image content if type is IMAGE    |
| `asFiles()`   | Optional\<List\<File\>\>  | File list if type is FILES        |

### TextContent

| Method        | Returns | Description        |
|---------------|---------|--------------------|
| `text()`      | String  | The text content   |
| `sizeBytes()` | long    | UTF-8 encoded size |

### ImageContent

| Method     | Returns       | Description                     |
|------------|---------------|---------------------------------|
| `image()`  | BufferedImage | The image content               |
| `width()`  | int           | Image width in pixels           |
| `height()` | int           | Image height in pixels          |
| `size()`   | long          | width × height × 4 (ARGB bytes) |

**Factory method**: `ImageContent.of(BufferedImage, String hash, Instant)` - auto-extracts dimensions.

### FilesContent

| Method        | Returns      | Description             |
|---------------|--------------|-------------------------|
| `files()`     | List\<File\> | Immutable file list     |
| `totalSize()` | long         | Total size of all files |

**Factory method**: `FilesContent.of(List<File>, String hash, Instant)` - auto-calculates size.

### UnknownContent

Represents unknown/unsupported clipboard content. `size()` always returns 0.

## ClipboardListener

Functional interface for receiving clipboard change notifications.

```java

@FunctionalInterface
public interface ClipboardListener {
    void onChange(ClipboardContent content);

    default void onError(Exception error) {
    }  // Optional error handling
}
```

**Thread Safety**: Callbacks invoked on virtual threads. Must be thread-safe if accessing shared state. Each listener
runs independently.

## ContentType

Enum for content classification.

| Value     | Description                     |
|-----------|---------------------------------|
| `TEXT`    | Plain text, HTML, RTF           |
| `IMAGE`   | PNG, JPEG, BMP as BufferedImage |
| `FILES`   | List of files                   |
| `UNKNOWN` | Unsupported type                |

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

### Pattern Matching (Type-Specific Properties)

```java
monitor.tryRead().ifPresent(content -> {
    if (content instanceof TextContent text) {
        System.out.println("Text length: " + text.sizeBytes());
    } else if (content instanceof ImageContent image) {
        System.out.println("Dimensions: " + image.width() + "x" + image.height());
    } else if (content instanceof FilesContent files) {
        System.out.println("Files: " + files.files().size());
    }
});
```

### Writing to Clipboard

```java
monitor.write("Hello World");                              // Text
monitor.write(ImageIO.read(new File("image.png")));       // Image
monitor.write(List.of(new File("doc.pdf")));              // Files
```

### Multiple Listeners

```java
ClipboardMonitor.builder()
    .detector(PollingDetector.defaults())
    .listener(content -> logChange(content))
    .listener(content -> syncToCloud(content))
    .listener(content -> updateUI(content))
    .build();
```

Each listener runs in its own virtual thread. Slow or failing listeners don't affect others.

### Custom Configuration

```java
ClipboardMonitor.builder()
    .detector(PollingDetector.builder()
        .interval(Duration.ofMillis(100))   // faster polling
        .build())
    .listener(this::handleChange)
    .debounce(Duration.ofMillis(30))        // shorter debounce
    .notifyOnStart(true)                     // get initial content
    .build();
```

---

## Exceptions

All exceptions extend `ClipboardException` (RuntimeException).

| Exception                       | When Thrown                             |
|---------------------------------|-----------------------------------------|
| `ClipboardException`            | Base exception for clipboard errors     |
| `ClipboardUnavailableException` | Clipboard locked by another application |

---

## Thread Safety & Resource Management

- All public methods are thread-safe
- `ClipboardContent` implementations are immutable records
- `ClipboardMonitor` implements `AutoCloseable` - always use try-with-resources
- `close()` is idempotent (safe to call multiple times)
- Listeners invoked on virtual threads (one per listener)

---

## Logging

The library uses SLF4J for logging. An SLF4J implementation is optional.

**Without implementation**: The library functions normally, all log statements are silently ignored.

**With implementation**: Add a runtime dependency like `logback-classic` or `slf4j-simple` to see log output.

**Log levels**:
- **DEBUG**: Internal flow, state changes, hash values
- **WARN**: Recoverable issues, fallback behavior
- **ERROR**: Failures with exception details

**Configuration**: Set logger `dev.bxlab.clipboard.monitor` to desired level.

---

## Required Imports

```java
import dev.bxlab.clipboard.monitor.core.ClipboardMonitor;
import dev.bxlab.clipboard.monitor.core.ClipboardListener;
import dev.bxlab.clipboard.monitor.content.ClipboardContent;
import dev.bxlab.clipboard.monitor.content.ContentType;
import dev.bxlab.clipboard.monitor.content.TextContent;
import dev.bxlab.clipboard.monitor.content.ImageContent;
import dev.bxlab.clipboard.monitor.content.FilesContent;
import dev.bxlab.clipboard.monitor.content.UnknownContent;
import dev.bxlab.clipboard.monitor.detector.PollingDetector;
import dev.bxlab.clipboard.monitor.detector.OwnershipDetector;
import dev.bxlab.clipboard.monitor.exception.ClipboardException;
import dev.bxlab.clipboard.monitor.exception.ClipboardUnavailableException;
```
