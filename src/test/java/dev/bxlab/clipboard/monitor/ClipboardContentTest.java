package dev.bxlab.clipboard.monitor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClipboardContentTest {

    @Nested
    class TextContentTests {

        @Test
        void shouldCreateWithAllFields() {
            String text = "Hello, World!";
            Instant now = Instant.now();

            TextContent content = new TextContent(text, "abc123", now, 13);

            assertThat(content.type()).isEqualTo(ContentType.TEXT);
            assertThat(content.text()).isEqualTo(text);
            assertThat(content.hash()).isEqualTo("abc123");
            assertThat(content.timestamp()).isEqualTo(now);
            assertThat(content.size()).isEqualTo(13);
        }

        @Test
        void shouldFailWithNullText() {
            Instant timestamp = Instant.now();

            assertThatThrownBy(() -> new TextContent(null, "hash", timestamp, 0))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("text");
        }

        @Test
        void shouldFailWithNullHash() {
            Instant timestamp = Instant.now();

            assertThatThrownBy(() -> new TextContent("test", null, timestamp, 4))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("hash");
        }

        @Test
        void shouldFailWithNullTimestamp() {
            assertThatThrownBy(() -> new TextContent("test", "hash", null, 4))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("timestamp");
        }

        @Test
        void shouldFailWithNegativeSizeBytes() {
            Instant timestamp = Instant.now();

            assertThatThrownBy(() -> new TextContent("test", "hash", timestamp, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sizeBytes");
        }

        @Test
        void shouldHaveTruncatedHashInToString() {
            TextContent content = new TextContent("test", "abcdefghijk", Instant.now(), 4);

            assertThat(content.toString())
                    .contains("TextContent")
                    .contains("abcdefgh...");
        }
    }

    @Nested
    class ImageContentTests {

        @Test
        void shouldCreateWithCalculatedSize() {
            BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
            Instant now = Instant.now();

            ImageContent content = ImageContent.of(image, "def456", now);

            assertThat(content.type()).isEqualTo(ContentType.IMAGE);
            assertThat(content.image()).isEqualTo(image);
            assertThat(content.width()).isEqualTo(100);
            assertThat(content.height()).isEqualTo(100);
            assertThat(content.size()).isEqualTo(100 * 100 * 4L);
        }

        @Test
        void shouldFailWithZeroWidth() {
            BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
            Instant timestamp = Instant.now();

            assertThatThrownBy(() -> new ImageContent(image, "hash", timestamp, 0, 100))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("width");
        }

        @Test
        void shouldFailWithNegativeHeight() {
            BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
            Instant timestamp = Instant.now();

            assertThatThrownBy(() -> new ImageContent(image, "hash", timestamp, 100, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("height");
        }
    }

    @Nested
    class FilesContentTests {

        @Test
        void shouldCreateWithExplicitSize() {
            List<File> files = List.of(new File("test.txt"), new File("test2.txt"));
            Instant now = Instant.now();

            FilesContent content = new FilesContent(files, "ghi789", now, 1024);

            assertThat(content.type()).isEqualTo(ContentType.FILES);
            assertThat(content.files()).containsExactlyElementsOf(files);
            assertThat(content.totalSize()).isEqualTo(1024);
        }

        @Test
        void shouldCreateWithCalculatedSize() {
            List<File> files = List.of(new File("test.txt"));

            FilesContent content = FilesContent.of(files, "hash", Instant.now());

            assertThat(content.files()).hasSize(1);
            assertThat(content.totalSize()).isGreaterThanOrEqualTo(0);
        }

        @Test
        void shouldMakeDefensiveCopyOfFiles() {
            List<File> files = new java.util.ArrayList<>();
            files.add(new File("test.txt"));

            FilesContent content = new FilesContent(files, "hash", Instant.now(), 100);

            files.add(new File("test2.txt"));

            assertThat(content.files()).hasSize(1);
        }

        @Test
        void shouldFailWithNegativeTotalSize() {
            List<File> files = List.of(new File("test.txt"));
            Instant timestamp = Instant.now();

            assertThatThrownBy(() -> new FilesContent(files, "hash", timestamp, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("totalSize");
        }
    }

    @Nested
    class UnknownContentTests {

        @Test
        void shouldCreateWithHashAndTimestamp() {
            Instant now = Instant.now();

            UnknownContent content = new UnknownContent("unknown123", now);

            assertThat(content.type()).isEqualTo(ContentType.UNKNOWN);
            assertThat(content.hash()).isEqualTo("unknown123");
            assertThat(content.timestamp()).isEqualTo(now);
            assertThat(content.size()).isZero();
        }
    }

    @Nested
    class ConvenienceMethodsTests {

        @Test
        void shouldReturnOptionalForText() {
            ClipboardContent textContent = new TextContent("hello", "hash", Instant.now(), 5);
            ClipboardContent imageContent = ImageContent.of(
                    new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB), "hash", Instant.now());

            assertThat(textContent.asText()).contains("hello");
            assertThat(imageContent.asText()).isEmpty();
        }

        @Test
        void shouldReturnOptionalForImage() {
            ClipboardContent textContent = new TextContent("hello", "hash", Instant.now(), 5);
            ClipboardContent imageContent = ImageContent.of(
                    new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB), "hash", Instant.now());

            assertThat(textContent.asImage()).isEmpty();
            assertThat(imageContent.asImage()).isPresent();
        }

        @Test
        void shouldReturnOptionalForFiles() {
            ClipboardContent textContent = new TextContent("hello", "hash", Instant.now(), 5);
            ClipboardContent filesContent = FilesContent.of(
                    List.of(new File("test.txt")), "hash", Instant.now());

            assertThat(textContent.asFiles()).isEmpty();
            assertThat(filesContent.asFiles()).isPresent();
        }
    }
}
