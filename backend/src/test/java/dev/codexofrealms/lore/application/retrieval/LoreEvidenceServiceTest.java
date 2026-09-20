package dev.codexofrealms.lore.application.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import dev.codexofrealms.content.SourceEvidenceAccess;
import dev.codexofrealms.content.SourcePassage;
import dev.codexofrealms.lore.RetrievedEvidence;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoreEvidenceServiceTest {
    @Test
    void shortQualificationIsNotCrowdedOutByRepeatedQuestionTerms() {
        var sources = mock(SourceEvidenceAccess.class);
        var chunk = chunk(1, "Maela calendario riesgo");
        when(sources.visibleParagraphs(any(), any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(List.of(new SourcePassage("Maela calendario", 0, 16),
                new SourcePassage("Maela calendario repetido", 20, 45), new SourcePassage("riesgo", 50, 56)));
        var result = new LoreEvidenceService(sources).passages(UUID.randomUUID(), UUID.randomUUID(),
            "Maela calendario riesgo", List.of(chunk), 2);
        assertThat(result).extracting(RetrievedEvidence::content).contains("riesgo");
    }
    @Test
    void reservesEvidenceFromLaterRelevantSourcesAndReadsEachVersionOnce() {
        var sources = mock(SourceEvidenceAccess.class);
        UUID realm = UUID.randomUUID(), user = UUID.randomUUID();
        var first = chunk(1, "Maela calendario");
        var later = chunk(7, "Maela riesgo");
        var paragraphs = java.util.stream.IntStream.range(0, 6)
            .mapToObj(i -> new SourcePassage("Maela calendario", i * 20, i * 20 + 16)).toList();
        when(sources.visibleParagraphs(realm, user, first.sourceDocumentId(), first.documentVersionId(), 0, Integer.MAX_VALUE))
            .thenReturn(paragraphs);
        when(sources.visibleParagraphs(realm, user, later.sourceDocumentId(), later.documentVersionId(), 0, Integer.MAX_VALUE))
            .thenReturn(List.of(new SourcePassage("Maela riesgo", 0, 12)));
        var result = new LoreEvidenceService(sources).passages(realm, user, "Maela calendario riesgo",
            List.of(first, first, first, first, first, first, later), 6);
        assertThat(result).hasSize(6);
        assertThat(result).extracting(RetrievedEvidence::rank).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(result).extracting(RetrievedEvidence::sourceDocumentId).contains(later.sourceDocumentId());
        assertThat(result).extracting(RetrievedEvidence::passageId).doesNotHaveDuplicates();
        verify(sources, times(1)).visibleParagraphs(realm, user, first.sourceDocumentId(), first.documentVersionId(), 0, Integer.MAX_VALUE);
    }

    @Test
    void irrelevantSourceDoesNotDisplaceRelevantPassageAtBudgetOne() {
        var sources = mock(SourceEvidenceAccess.class);
        var irrelevant = chunk(1, "Piedras"); var relevant = chunk(10, "Maela riesgo");
        when(sources.visibleParagraphs(any(), any(), eq(irrelevant.sourceDocumentId()), any(), anyInt(), anyInt()))
            .thenReturn(List.of(new SourcePassage("Piedras", 0, 7)));
        when(sources.visibleParagraphs(any(), any(), eq(relevant.sourceDocumentId()), any(), anyInt(), anyInt()))
            .thenReturn(List.of(new SourcePassage("Maela riesgo", 0, 12)));
        var result = new LoreEvidenceService(sources).passages(UUID.randomUUID(), UUID.randomUUID(), "Maela riesgo",
            List.of(irrelevant, relevant), 1);
        assertThat(result).extracting(RetrievedEvidence::sourceDocumentId).containsExactly(relevant.sourceDocumentId());
    }

    private static RetrievedEvidence chunk(int rank, String content) {
        return new RetrievedEvidence(rank, .1, .9, UUID.randomUUID(), content, "Heading", 0, 2000,
            UUID.randomUUID(), UUID.randomUUID(), 1, "Source", "source.md", "checksum", UUID.randomUUID(), "PUBLIC");
    }
}
