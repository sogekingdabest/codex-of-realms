package dev.codexofrealms.qa.application.answering;

import dev.codexofrealms.content.SourceEvidenceAccess;
import dev.codexofrealms.content.application.evidence.*;
import dev.codexofrealms.content.application.port.*;
import dev.codexofrealms.lore.LoreSearch;
import dev.codexofrealms.lore.LoreEvidence;
import dev.codexofrealms.lore.application.retrieval.ReferenceLoreEvidenceService;
import dev.codexofrealms.qa.application.port.GroundedAnswerModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Frozen pre-change behavior, enabled ONLY in this opt-in evaluation. Never a production setting. */
@TestConfiguration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ia.pipeline", havingValue = "reference")
class ReferenceEvaluationConfiguration {
    @Bean @Primary
    LoreEvidence referenceEvidence(SourceEvidenceService service, SourceRepository repository, RawSourceStorage storage) {
        return new ReferenceLoreEvidenceService(new SourceEvidenceAccess(service, new ReferenceVisiblePassageService(repository, storage)));
    }
    @Bean @Primary
    QuestionAnsweringService referenceAnswering(LoreSearch search, LoreEvidence evidence, GroundedAnswerModel model,
                                               AnsweringProperties properties, AnswerValidator validator, QaMetrics metrics) {
        return new ReferenceQuestionAnsweringService(search, evidence, model,
            new ReferenceEvidenceGate(properties), validator, metrics, properties);
    }
}
