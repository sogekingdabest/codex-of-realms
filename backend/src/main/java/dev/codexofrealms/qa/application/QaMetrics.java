package dev.codexofrealms.qa.application;

import dev.codexofrealms.qa.AnswerOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
class QaMetrics {

    private static final String OUTCOME_TAG = "outcome";
    private static final String REASON_TAG = "reason";
    private static final String DURATION_METRIC = "codex.qa.duration";

    private final MeterRegistry registry;

    QaMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    Timer.Sample start() {
        return Timer.start(registry);
    }

    void gateRejected(Timer.Sample sample, String reason) {
        registry.counter("codex.qa.outcomes", OUTCOME_TAG, "insufficient_evidence", "stage", "gate").increment();
        sample.stop(registry.timer(DURATION_METRIC, OUTCOME_TAG, "insufficient_evidence", REASON_TAG, reason));
    }

    void completed(Timer.Sample sample, AnswerOutcome outcome) {
        String value = outcome.name().toLowerCase(java.util.Locale.ROOT);
        registry.counter("codex.qa.outcomes", OUTCOME_TAG, value, "stage", "validation").increment();
        sample.stop(registry.timer(DURATION_METRIC, OUTCOME_TAG, value, REASON_TAG, "none"));
    }

    void failed(Timer.Sample sample) {
        sample.stop(registry.timer(DURATION_METRIC, OUTCOME_TAG, "failure", REASON_TAG, "exception"));
    }
}
