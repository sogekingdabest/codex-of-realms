package dev.codexofrealms.qa.application.answering;

import static dev.codexofrealms.qa.application.answering.EvidenceGateDecision.Reason.DIRECT_INJECTION;
import static dev.codexofrealms.qa.application.answering.EvidenceGateDecision.Reason.INDIRECT_INJECTION;
import static dev.codexofrealms.qa.application.answering.EvidenceGateDecision.Reason.LOW_QUESTION_COVERAGE;
import static dev.codexofrealms.qa.application.answering.EvidenceGateDecision.Reason.LOW_SIMILARITY;
import static dev.codexofrealms.qa.application.answering.EvidenceGateDecision.Reason.NO_EVIDENCE;

import dev.codexofrealms.lore.RetrievedEvidence;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class EvidenceGate {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        Pattern.compile("\\bignora(?:r)?\\b.{0,40}\\b(reglas|instrucciones|sistema|anterior(?:es)?)\\b"),
        Pattern.compile("\\bactua\\s+como\\b"),
        Pattern.compile("\\b(system prompt|mensaje del sistema)\\b"),
        Pattern.compile("\\bomite\\b.{0,30}\\b(reglas|restricciones|seguridad)\\b"),
        Pattern.compile("\\b(instruccion|instruction)(?:es)?\\s+(para|al)\\s+(el\\s+)?(asistente|modelo|assistant)\\b"),
        Pattern.compile("\\b(begin|inicio)\\s+(system|instructions|instrucciones)\\b")
    );

    @org.springframework.beans.factory.annotation.Value("${codex.retrieval.lexical-minimum-coverage:1.0}")
    private double lexicalMinimumCoverage = 1.0;

    @jakarta.annotation.PostConstruct
    void validateLexicalThreshold() {
        if (!Double.isFinite(lexicalMinimumCoverage) || lexicalMinimumCoverage < 0 || lexicalMinimumCoverage > 1)
            throw new IllegalArgumentException("Lexical coverage must be between zero and one.");
    }
    private final AnsweringProperties properties;

    EvidenceGate(AnsweringProperties properties) {
        this.properties = properties;
    }

    EvidenceGateDecision evaluate(String question, List<RetrievedEvidence> retrieved) {
        if (containsInjection(question)) return EvidenceGateDecision.reject(DIRECT_INJECTION);
        if (retrieved.isEmpty()) return EvidenceGateDecision.reject(NO_EVIDENCE);

        List<RetrievedEvidence> evidence = retrieved.stream()
            .limit(properties.retrievalLimit())
            .toList();
        if (evidence.stream().map(RetrievedEvidence::content).anyMatch(EvidenceGate::containsInjection)) {
            return EvidenceGateDecision.reject(INDIRECT_INJECTION);
        }
        double bestSimilarity = evidence.stream()
            .mapToDouble(RetrievedEvidence::similarity)
            .max()
            .orElse(Double.NEGATIVE_INFINITY);
        if (bestSimilarity < properties.minimumSimilarity()
            && evidence.stream().noneMatch(p -> p.retrievalSignals().sufficient(lexicalMinimumCoverage))) {
            return EvidenceGateDecision.reject(LOW_SIMILARITY);
        }

        return EvidenceGateDecision.accept(evidence);
    }

    private static boolean containsInjection(String text) {
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replaceAll("\\s+", " ");
        return INJECTION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }

    EvidenceGateDecision screenPassages(String question, List<RetrievedEvidence> passages) {
        if (passages.isEmpty()) return EvidenceGateDecision.reject(NO_EVIDENCE);
        if (passages.stream().map(RetrievedEvidence::content).anyMatch(EvidenceGate::containsInjection)) {
            return EvidenceGateDecision.reject(INDIRECT_INJECTION);
        }
        String combined = passages.stream().map(RetrievedEvidence::content).collect(java.util.stream.Collectors.joining("\n"));
        if (dev.codexofrealms.lore.PassageRelevance.coverage(question, combined) < properties.minimumQuestionCoverage()) {
            return EvidenceGateDecision.reject(LOW_QUESTION_COVERAGE);
        }
        return EvidenceGateDecision.accept(passages);
    }
}
