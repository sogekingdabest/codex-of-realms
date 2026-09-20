package dev.codexofrealms.content.application.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import dev.codexofrealms.content.SourcePassage;
import dev.codexofrealms.content.application.port.RawSourceStorage;
import dev.codexofrealms.content.application.port.SourceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VisiblePassageServiceTest {
    @Test
    void contextualWindowsPreserveOriginalSeparatorsAndStopAtHeadingsAndExcludedText() {
        String body="La ciudad se desplaza 🧭.\r\n\r\nLos archivos se queman.\r\n\r\nEste es el motivo de las diferencias.";
        String text="# Causa\r\n"+body+"\r\n\r\n# Otra sección\r\nOtra causa.";
        var result=VisiblePassageService.contextualSplit(text);
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().content()).isEqualTo(body);
        result.forEach(p -> assertThat(text.substring(p.startOffset(),p.endOffset())).isEqualTo(p.content()));
        String excluded="Causa.\n\n"+"x".repeat(2001)+"\n\nConsecuencia.";
        assertThat(VisiblePassageService.contextualSplit(excluded)).hasSize(2);
        String exact="a".repeat(990)+"\n\n"+"b".repeat(1008);
        assertThat(VisiblePassageService.contextualSplit(exact)).singleElement().satisfies(p -> assertThat(p.content()).hasSize(2000));
        assertThat(VisiblePassageService.contextualSplit(exact+"b")).hasSize(2);
    }

    @Test
    void revalidatesAllSelectedPassagesWithOneFreshReadPerVersion() {
        var repository = mock(SourceRepository.class);
        var storage = mock(RawSourceStorage.class);
        var version = mock(dev.codexofrealms.content.application.port.SourceVersion.class);
        when(version.storageKey()).thenReturn("original.md");
        when(repository.findAccessibleVersion(any(), any(), any(), any())).thenReturn(Optional.of(version), Optional.empty());
        when(storage.read("original.md")).thenReturn("Uno. Dos.".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var service = new VisiblePassageService(repository, storage);
        var selected = java.util.List.of(new SourcePassage("Uno.", 0, 4), new SourcePassage("Dos.", 5, 9));
        UUID realm = UUID.randomUUID(), user = UUID.randomUUID(), doc = UUID.randomUUID(), id = UUID.randomUUID();
        assertThat(service.matchesAll(realm, user, doc, id, selected)).isTrue();
        assertThat(service.matchesAll(realm, user, doc, id, selected)).isFalse();
        verify(storage, times(1)).read("original.md");
    }
    @Test
    void exactLimitAndOversizedSentenceDiagnostics() {
        assertThat(VisiblePassageService.split("a".repeat(2000))).hasSize(1);
        var result = VisiblePassageService.analyze("a".repeat(2001));
        assertThat(result.passages()).isEmpty();
        assertThat(result.excludedSentences()).isEqualTo(1);
        var view = new dev.codexofrealms.content.application.source.SourceContentView(
            UUID.randomUUID(), UUID.randomUUID(), "Existing source", "source.md", "a".repeat(2001));
        assertThat(view.excludedSentences()).isEqualTo(1);
    }

    @Test
    void longParagraphWindowsOverlapWithoutChangingUtf16TextOrDroppingSentences() {
        String sentence = "Nara no puede abrir la puerta 🧭 salvo con permiso. ";
        String text = "# Umbral\r\n\r\n" + sentence.repeat(100);
        var result = VisiblePassageService.analyze(text);
        assertThat(result.excludedSentences()).isZero();
        assertThat(result.passages().size()).isGreaterThan(1);
        int end = 0;
        for (var p : result.passages()) {
            assertThat(p.content().length()).isLessThanOrEqualTo(2000);
            assertThat(p.content()).isEqualTo(text.substring(p.startOffset(), p.endOffset()));
            if (end > 0) assertThat(p.startOffset()).isLessThan(end);
            end = p.endOffset();
        }
        assertThat(end).isEqualTo(text.stripTrailing().length());
    }

    @Test
    void exclusionDoesNotJoinTextAcrossAnOversizedSentence() {
        String text = "Antes hay permiso. " + "X".repeat(2001) + ". Después no hay permiso.";
        var result = VisiblePassageService.analyze(text);
        assertThat(result.excludedSentences()).isEqualTo(1);
        assertThat(result.passages()).extracting(SourcePassage::content)
            .containsExactly("Antes hay permiso.", "Después no hay permiso.");
    }
    @Test
    void preservesParagraphBoundariesAndExactUtf16Offsets() {
        String text = "# Título\r\n\r\nNara no pertenece. 🧭\r\nTiene 42 monedas.\r\n\r\n## Después\r\nOtro párrafo.";
        var passages = VisiblePassageService.split(text);
        assertThat(passages).hasSize(2);
        assertThat(passages.getFirst().content()).isEqualTo("Nara no pertenece. 🧭\r\nTiene 42 monedas.");
        for (var p : passages) assertThat(text.substring(p.startOffset(), p.endOffset())).isEqualTo(p.content());
    }

    @Test
    void omitsOversizedParagraphInsteadOfRemovingItsContext() {
        assertThat(VisiblePassageService.split("No " + "a".repeat(2000))).isEmpty();
    }

    @Test
    void revokedOrRetiredEvidenceCannotPassRevalidation() {
        var repository = mock(SourceRepository.class);
        var storage = mock(RawSourceStorage.class);
        UUID realm = UUID.randomUUID(), user = UUID.randomUUID(), doc = UUID.randomUUID(), version = UUID.randomUUID();
        when(repository.findAccessibleVersion(realm, doc, version, user)).thenReturn(Optional.empty());
        assertThat(new VisiblePassageService(repository, storage).matches(realm, user, doc, version,
            new SourcePassage("Nara", 0, 4))).isFalse();
        verifyNoInteractions(storage);
    }
}
