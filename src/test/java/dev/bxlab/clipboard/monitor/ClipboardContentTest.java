package dev.bxlab.clipboard.monitor;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClipboardContentTest {

    @Test
    void shouldCreateTextContent() {
        String text = "Hello, World!";

        ClipboardContent content = ClipboardContent.builder()
                .textData(text)
                .hash("abc123")
                .size(text.length())
                .build();

        assertThat(content.getType()).isEqualTo(ContentType.TEXT);
        assertThat(content.asText()).contains(text);
        assertThat(content.asImage()).isEmpty();
        assertThat(content.asFileList()).isEmpty();
        assertThat(content.getHash()).isEqualTo("abc123");
        assertThat(content.getSize()).isEqualTo(text.length());
        assertThat(content.getTimestamp()).isNotNull();
    }

    @Test
    void shouldCreateImageContent() {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);

        ClipboardContent content = ClipboardContent.builder()
                .imageData(image)
                .hash("def456")
                .size(40000)
                .build();

        assertThat(content.getType()).isEqualTo(ContentType.IMAGE);
        assertThat(content.asText()).isEmpty();
        assertThat(content.asImage()).contains(image);
        assertThat(content.asFileList()).isEmpty();
    }

    @Test
    void shouldCreateFileListContent() {
        List<File> files = List.of(new File("test.txt"), new File("test2.txt"));

        ClipboardContent content = ClipboardContent.builder()
                .fileListData(files)
                .hash("ghi789")
                .size(0)
                .build();

        assertThat(content.getType()).isEqualTo(ContentType.FILE_LIST);
        assertThat(content.asText()).isEmpty();
        assertThat(content.asImage()).isEmpty();
        assertThat(content.asFileList()).contains(files);
    }

    @Test
    void shouldCreateUnknownContent() {
        ClipboardContent content = ClipboardContent.builder()
                .unknownType()
                .hash("unknown123")
                .size(0)
                .build();

        assertThat(content.getType()).isEqualTo(ContentType.UNKNOWN);
        assertThat(content.asText()).isEmpty();
        assertThat(content.asImage()).isEmpty();
        assertThat(content.asFileList()).isEmpty();
    }

    @Test
    void shouldUseCustomTimestamp() {
        Instant customTime = Instant.parse("2025-01-01T00:00:00Z");

        ClipboardContent content = ClipboardContent.builder()
                .textData("test")
                .hash("test")
                .size(4)
                .timestamp(customTime)
                .build();

        assertThat(content.getTimestamp()).isEqualTo(customTime);
    }

    @Test
    void shouldReturnBytesForText() {
        String text = "Test text";

        ClipboardContent content = ClipboardContent.builder()
                .textData(text)
                .hash("test")
                .size(text.length())
                .build();

        byte[] bytes = content.asBytes();
        assertThat(new String(bytes)).isEqualTo(text);
    }

    @Test
    void shouldReturnEmptyBytesForImage() {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);

        ClipboardContent content = ClipboardContent.builder()
                .imageData(image)
                .hash("test")
                .size(400)
                .build();

        byte[] bytes = content.asBytes();
        assertThat(bytes).isEmpty();
    }

    @Test
    void shouldBeEqualByHash() {
        ClipboardContent content1 = ClipboardContent.builder()
                .textData("text1")
                .hash("samehash")
                .size(5)
                .build();

        ClipboardContent content2 = ClipboardContent.builder()
                .textData("text2")
                .hash("samehash")
                .size(5)
                .build();

        assertThat(content1).isEqualTo(content2);
        assertThat(content1.hashCode()).hasSameHashCodeAs(content2.hashCode());
    }

    @Test
    void shouldNotBeEqualWithDifferentHash() {
        ClipboardContent content1 = ClipboardContent.builder()
                .textData("text")
                .hash("hash1")
                .size(4)
                .build();

        ClipboardContent content2 = ClipboardContent.builder()
                .textData("text")
                .hash("hash2")
                .size(4)
                .build();

        assertThat(content1).isNotEqualTo(content2);
    }

    @Test
    void shouldHaveReadableToString() {
        ClipboardContent content = ClipboardContent.builder()
                .textData("test")
                .hash("abcdefghijk")
                .size(4)
                .build();

        String str = content.toString();
        assertThat(str)
                .contains("TEXT")
                .contains("abcdefgh...")
                .contains("size=4");
    }

    @Test
    void shouldFailWithNullType() {
        ClipboardContent.Builder builder = ClipboardContent.builder()
                .hash("test")
                .size(0);

        assertThatThrownBy(builder::build)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type must be set");
    }

    @Test
    void shouldFailWithNullHash() {
        ClipboardContent.Builder builder = ClipboardContent.builder()
                .textData("test")
                .size(0);

        assertThatThrownBy(builder::build)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("hash must be set");
    }

    @Test
    void shouldEnforceTypeDataConsistencyByDesign() {
        ClipboardContent content = ClipboardContent.builder()
                .textData("test")
                .hash("hash")
                .size(4)
                .build();

        assertThat(content.getType()).isEqualTo(ContentType.TEXT);
        assertThat(content.asText()).contains("test");
    }
}
