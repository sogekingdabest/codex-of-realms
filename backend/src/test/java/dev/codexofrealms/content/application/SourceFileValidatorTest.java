package dev.codexofrealms.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SourceFileValidatorTest {

    private final SourceFileValidator validator = new SourceFileValidator(
        new IngestionProperties(32, 200, 20, 8, "test", "deterministic")
    );

    @Test
    void acceptsUtf8MarkdownAndNormalizesAClientPath() {
        AcceptedSource source = validator.validate(
            "# Lumbrevela".getBytes(StandardCharsets.UTF_8),
            "C:\\fakepath\\lore.md", "text/markdown;charset=UTF-8"
        );

        assertThat(source.originalFilename()).isEqualTo("lore.md");
        assertThat(source.mediaType()).isEqualTo("text/markdown");
        assertThat(source.checksum()).hasSize(64);
    }

    @Test
    void rejectsUnsupportedExtensionsEvenWhenMimeClaimsText() {
        assertThatThrownBy(() -> validator.validate(
            "lore".getBytes(StandardCharsets.UTF_8), "lore.html", "text/plain"
        )).isInstanceOf(InvalidSourceException.class);
    }

    @Test
    void rejectsMalformedUtf8AndOversizedSources() {
        assertThatThrownBy(() -> validator.validate(new byte[] {(byte) 0xc3, 0x28}, "lore.txt", "text/plain"))
            .isInstanceOf(InvalidSourceException.class);
        assertThatThrownBy(() -> validator.validate(new byte[33], "lore.txt", "text/plain"))
            .isInstanceOf(InvalidSourceException.class);
    }
}
