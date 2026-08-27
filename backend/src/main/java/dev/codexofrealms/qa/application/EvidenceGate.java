package dev.codexofrealms.qa.application;

import dev.codexofrealms.lore.RetrievedEvidence;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class EvidenceGate {

    private static final List<Pattern> DIRECT_INJECTION_PATTERNS = List.of(
        Pattern.compile("\\bignora(?:r)?\\b.{0,40}\\b(reglas|instrucciones|sistema|anterior(?:es)?)\\b"),
        Pattern.compile("\\bactua\\s+como\\b"),
        Pattern.compile("\\b(system prompt|mensaje del sistema)\\b"),
        Pattern.compile("\\bomite\\b.{0,30}\\b(reglas|restricciones|seguridad)\\b")
    );

    private final QaProperties properties;

    EvidenceGate(QaProperties properties) {
        this.properties = properties;
    }

    EvidenceGateDecision evaluate(String question, List<RetrievedEvidence> retrieved) {
        if (isDirectInjection(question)) return EvidenceGateDecision.reject("direct_injection");
        if (retrieved.isEmpty()) return EvidenceGateDecision.reject("no_evidence");

        List<RetrievedEvidence> evidence = retrieved.stream()
            .limit(properties.maxEvidenceChunks())
            .toList();
        double bestSimilarity = evidence.stream()
            .mapToDouble(RetrievedEvidence::similarity)
            .max()
            .orElse(Double.NEGATIVE_INFINITY);
        if (bestSimilarity < properties.minimumSimilarity()) {
            return EvidenceGateDecision.reject("low_similarity");
        }

        String combinedEvidence = evidence.stream()
            .map(RetrievedEvidence::content)
            .reduce("", (left, right) -> left + "\n" + right);
        double coverage = TextTerms.coverage(question, combinedEvidence);
        if (coverage < properties.minimumQuestionCoverage()) {
            return EvidenceGateDecision.reject("low_question_coverage");
        }
        return new EvidenceGateDecision(true, "sufficient", evidence);
    }

    private static boolean isDirectInjection(String question) {
        String normalized = Normalizer.normalize(question.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", " ")
            .replaceAll("\\s+", " ");
        return DIRECT_INJECTION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }
}
