package dev.codexofrealms.qa.application.answering;

import dev.codexofrealms.qa.AnswerOutcome;
import dev.codexofrealms.qa.LoreAnswer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
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

    void refused(Timer.Sample sample, String stage, String reason) {
        registry.counter("codex.qa.outcomes", OUTCOME_TAG, "insufficient_evidence", "stage", stage).increment();
        sample.stop(registry.timer(DURATION_METRIC, OUTCOME_TAG, "insufficient_evidence", REASON_TAG, reason));
    }

    void completed(Timer.Sample sample, LoreAnswer answer) {
        String outcome = answer.outcome().name().toLowerCase(Locale.ROOT);
        String reason = answer.outcome() == AnswerOutcome.ANSWERED
            ? "none"
            : answer.failureReason().name().toLowerCase(Locale.ROOT);
        registry.counter("codex.qa.outcomes", OUTCOME_TAG, outcome, "stage", "validation").increment();
        sample.stop(registry.timer(DURATION_METRIC, OUTCOME_TAG, outcome, REASON_TAG, reason));
    }

    void failed(Timer.Sample sample) {
        sample.stop(registry.timer(DURATION_METRIC, OUTCOME_TAG, "failure", REASON_TAG, "exception"));
    }
}
