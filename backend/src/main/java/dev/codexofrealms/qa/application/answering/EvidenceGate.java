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

    private final AnsweringProperties properties;

    EvidenceGate(AnsweringProperties properties) {
        this.properties = properties;
    }

    EvidenceGateDecision evaluate(String question, List<RetrievedEvidence> retrieved) {
        if (containsInjection(question)) return EvidenceGateDecision.reject(DIRECT_INJECTION);
        if (retrieved.isEmpty()) return EvidenceGateDecision.reject(NO_EVIDENCE);

        List<RetrievedEvidence> evidence = retrieved.stream()
            .limit(properties.maxEvidenceChunks())
            .toList();
        if (evidence.stream().map(RetrievedEvidence::content).anyMatch(EvidenceGate::containsInjection)) {
            return EvidenceGateDecision.reject(INDIRECT_INJECTION);
        }
        double bestSimilarity = evidence.stream()
            .mapToDouble(RetrievedEvidence::similarity)
            .max()
            .orElse(Double.NEGATIVE_INFINITY);
        if (bestSimilarity < properties.minimumSimilarity()) {
            return EvidenceGateDecision.reject(LOW_SIMILARITY);
        }

        String combinedEvidence = evidence.stream()
            .map(RetrievedEvidence::content)
            .reduce("", (left, right) -> left + "\n" + right);
        double coverage = TextTerms.coverage(question, combinedEvidence);
        if (coverage < properties.minimumQuestionCoverage()) {
            return EvidenceGateDecision.reject(LOW_QUESTION_COVERAGE);
        }
        return EvidenceGateDecision.accept(evidence);
    }

    private static boolean containsInjection(String text) {
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", " ")
            .replaceAll("\\s+", " ");
        return INJECTION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }
}
