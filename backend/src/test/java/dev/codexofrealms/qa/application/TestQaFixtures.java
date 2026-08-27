package dev.codexofrealms.qa.application;

import dev.codexofrealms.lore.RetrievedEvidence;
import java.util.UUID;

public final class TestQaFixtures {

    private TestQaFixtures() {
    }

    static QaProperties properties() {
        return new QaProperties(
            10, 6, 0.45, 0.70, 0.35, 6, 2000,
            "test", "chat-v1", 8192, 768, "5m"
        );
    }

    public static RetrievedEvidence evidence(int rank, double similarity, String content) {
        return new RetrievedEvidence(
            rank, 1.0 - similarity, similarity, UUID.randomUUID(), content, "Sección", 10, 90,
            UUID.randomUUID(), UUID.randomUUID(), 1, "Fuente visible", "fuente.md",
            "0123456789abcdef", UUID.randomUUID(), "PUBLIC"
        );
    }
}
