package dev.codexofrealms.content.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StructuralChunkerTest {

    private final StructuralChunker chunker = new StructuralChunker(
        new IngestionProperties(1024, 200, 20, 8, "test", "deterministic")
    );

    @Test
    void preservesMarkdownHeadingsAndExactOffsets() {
        String text = "# La Aguja\n\n" + "Una verdad antigua. ".repeat(18)
            + "\n\n## Nara\n\nNara recuerda el meridiano.";

        List<SourceChunk> chunks = chunker.split(text);

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks.getFirst().heading()).isEqualTo("La Aguja");
        assertThat(chunks.getLast().heading()).isEqualTo("Nara");
        assertThat(chunks).allSatisfy(chunk ->
            assertThat(text.substring(chunk.startOffset(), chunk.endOffset()))
                .isEqualTo(chunk.content())
        );
    }

    @Test
    void keepsOrdinalsContinuous() {
        List<SourceChunk> chunks = chunker.split("Texto breve sin encabezado.");
        assertThat(chunks).extracting(SourceChunk::ordinal).containsExactly(0);
    }
}
