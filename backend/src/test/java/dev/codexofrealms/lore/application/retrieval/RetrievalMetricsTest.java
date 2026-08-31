package dev.codexofrealms.lore.application.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codexofrealms.lore.RetrievedEvidence;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetrievalMetricsTest {

    @Test
    void recordsBoundedLowCardinalityRetrievalMeasurements() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RetrievalMetrics metrics = new RetrievalMetrics(registry);
        RetrievedEvidence evidence = new RetrievedEvidence(
            1, 0.25, 0.75, UUID.randomUUID(), "evidence", "heading", 0, 8,
            UUID.randomUUID(), UUID.randomUUID(), 1, "source", "source.md",
            "a".repeat(64), UUID.randomUUID(), "PUBLIC"
        );

        metrics.success(metrics.start(), List.of(evidence));

        assertThat(registry.get("codex.retrieval.duration").tag("outcome", "success")
            .timer().count()).isEqualTo(1);
        assertThat(registry.get("codex.retrieval.results").summary().totalAmount())
            .isEqualTo(1.0);
        assertThat(registry.get("codex.retrieval.distance").summary().totalAmount())
            .isEqualTo(0.25);
    }
}
