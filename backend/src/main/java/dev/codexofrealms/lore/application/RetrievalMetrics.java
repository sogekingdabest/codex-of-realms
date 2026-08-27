package dev.codexofrealms.lore.application;

import dev.codexofrealms.lore.RetrievedEvidence;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class RetrievalMetrics {

    private final MeterRegistry registry;
    private final DistributionSummary resultCount;
    private final DistributionSummary distance;

    RetrievalMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.resultCount = DistributionSummary.builder("codex.retrieval.results")
            .description("Number of authorized evidence chunks returned")
            .register(registry);
        this.distance = DistributionSummary.builder("codex.retrieval.distance")
            .description("Cosine distance of authorized retrieval results")
            .maximumExpectedValue(2.0)
            .register(registry);
    }

    Timer.Sample start() {
        return Timer.start(registry);
    }

    void success(Timer.Sample sample, List<RetrievedEvidence> evidence) {
        sample.stop(registry.timer("codex.retrieval.duration", "outcome", "success"));
        resultCount.record(evidence.size());
        evidence.forEach(item -> distance.record(item.distance()));
    }

    void failure(Timer.Sample sample) {
        sample.stop(registry.timer("codex.retrieval.duration", "outcome", "failure"));
    }
}
