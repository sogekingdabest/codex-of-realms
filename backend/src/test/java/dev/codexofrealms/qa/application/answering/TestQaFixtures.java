package dev.codexofrealms.qa.application.answering;

import dev.codexofrealms.lore.RetrievedEvidence;
import java.util.UUID;

public final class TestQaFixtures {

    private TestQaFixtures() {
    }

    static AnsweringProperties properties() {
        return new AnsweringProperties(10, 6, 0.45, 0.70, 3, 6000);
    }

    public static RetrievedEvidence evidence(int rank, double similarity, String content) {
        return new RetrievedEvidence(
            rank, 1.0 - similarity, similarity, UUID.randomUUID(), content, "Sección", 10, 90,
            UUID.randomUUID(), UUID.randomUUID(), 1, "Fuente visible", "fuente.md",
            "0123456789abcdef", UUID.randomUUID(), "PUBLIC"
        );
    }

    public static dev.codexofrealms.lore.LoreEvidence passthroughEvidence() {
        return new dev.codexofrealms.lore.LoreEvidence() {
            public java.util.List<RetrievedEvidence> passages(UUID realm, UUID user, String question, java.util.List<RetrievedEvidence> chunks, int limit) {
                return chunks.stream().limit(limit).toList();
            }
            public boolean stillVisible(UUID realm, UUID user, java.util.List<RetrievedEvidence> passages) { return true; }
        };
    }

    public static dev.codexofrealms.lore.LoreEvidence paragraphEvidence() {
        return new dev.codexofrealms.lore.LoreEvidence() {
            public java.util.List<RetrievedEvidence> passages(UUID realm, UUID user, String question, java.util.List<RetrievedEvidence> chunks, int limit) {
                var result = new java.util.ArrayList<RetrievedEvidence>();
                for (var c : chunks) {
                    for (var p : dev.codexofrealms.content.application.evidence.VisiblePassageService.split(c.content())) {
                        result.add(new RetrievedEvidence(result.size() + 1, c.distance(), c.similarity(), c.chunkId(),
                            p.content(), c.heading(), c.startOffset() + p.startOffset(), c.startOffset() + p.endOffset(), c.sourceDocumentId(),
                            c.documentVersionId(), c.versionNumber(), c.sourceTitle(), c.originalFilename(),
                            c.checksumSha256(), c.accessPolicyId(), c.accessClassification()));

                    }
                }
                return dev.codexofrealms.lore.PassageSelection.select(question, result, limit);
            }
            public boolean stillVisible(UUID realm, UUID user, java.util.List<RetrievedEvidence> passages) { return true; }
        };
    }
}
