package dev.codexofrealms.qa.application;

import dev.codexofrealms.qa.AnswerOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
class QaMetrics {

    private final MeterRegistry registry;

    QaMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    Timer.Sample start() {
        return Timer.start(registry);
    }

    void gateRejected(Timer.Sample sample, String reason) {
        registry.counter("codex.qa.outcomes", "outcome", "insufficient_evidence", "stage", "gate").increment();
        sample.stop(registry.timer("codex.qa.duration", "outcome", "insufficient_evidence", "reason", reason));
    }

    void completed(Timer.Sample sample, AnswerOutcome outcome) {
        String value = outcome.name().toLowerCase(java.util.Locale.ROOT);
        registry.counter("codex.qa.outcomes", "outcome", value, "stage", "validation").increment();
        sample.stop(registry.timer("codex.qa.duration", "outcome", value, "reason", "none"));
    }

    void failed(Timer.Sample sample) {
        sample.stop(registry.timer("codex.qa.duration", "outcome", "failure", "reason", "exception"));
    }
}
