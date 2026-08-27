package dev.codexofrealms.qa.application;

import dev.codexofrealms.lore.RetrievedEvidence;
import dev.codexofrealms.qa.AnswerOutcome;
import dev.codexofrealms.qa.AnswerProvenance;
import dev.codexofrealms.qa.Citation;
import dev.codexofrealms.qa.DraftClaim;
import dev.codexofrealms.qa.GroundedAnswerDraft;
import dev.codexofrealms.qa.LoreAnswer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AnswerValidator {

    private final QaProperties properties;

    AnswerValidator(QaProperties properties) {
        this.properties = properties;
    }

    LoreAnswer validate(
        UUID realmId,
        GroundedAnswerDraft draft,
        List<RetrievedEvidence> visibleEvidence,
        AnswerProvenance provenance
    ) {
        if (draft == null || draft.outcome() != AnswerOutcome.ANSWERED) {
            return LoreAnswer.insufficient(provenance);
        }
        if (draft.claims().isEmpty() || draft.claims().size() > properties.maxClaims()) {
            return LoreAnswer.insufficient(provenance);
        }

        Map<Integer, RetrievedEvidence> byRank = new LinkedHashMap<>();
        visibleEvidence.forEach(item -> byRank.put(item.rank(), item));
        List<String> renderedClaims = new ArrayList<>();
        LinkedHashSet<Integer> citedRanks = new LinkedHashSet<>();
        int answerCharacters = 0;

        for (DraftClaim claim : draft.claims()) {
            if (!validText(claim.text()) || claim.citations().isEmpty() || claim.citations().size() > 3) {
                return LoreAnswer.insufficient(provenance);
            }
            List<Integer> uniqueRanks = claim.citations().stream().distinct().sorted().toList();
            if (uniqueRanks.size() != claim.citations().size()
                || uniqueRanks.stream().anyMatch(rank -> !byRank.containsKey(rank))) {
                return LoreAnswer.insufficient(provenance);
            }
            String support = uniqueRanks.stream()
                .map(byRank::get)
                .map(RetrievedEvidence::content)
                .reduce("", (left, right) -> left + "\n" + right);
            if (TextTerms.coverage(claim.text(), support) < properties.minimumClaimCoverage()) {
                return LoreAnswer.insufficient(provenance);
            }
            String text = claim.text().strip().replaceAll("\\s+", " ");
            answerCharacters += text.length();
            if (answerCharacters > properties.maxAnswerCharacters()) {
                return LoreAnswer.insufficient(provenance);
            }
            String markers = uniqueRanks.stream().map(rank -> "[" + rank + "]")
                .reduce((left, right) -> left + " " + right).orElseThrow();
            renderedClaims.add(text + " " + markers);
            citedRanks.addAll(uniqueRanks);
        }

        List<Citation> citations = citedRanks.stream()
            .map(byRank::get)
            .map(item -> citation(realmId, item))
            .toList();
        return new LoreAnswer(AnswerOutcome.ANSWERED, String.join("\n", renderedClaims), citations, provenance);
    }

    private static boolean validText(String text) {
        return text != null && !text.isBlank() && text.length() <= 500
            && text.chars().noneMatch(Character::isISOControl);
    }

    private static Citation citation(UUID realmId, RetrievedEvidence item) {
        return new Citation(
            item.rank(), realmId, item.chunkId(), item.sourceDocumentId(), item.documentVersionId(),
            item.versionNumber(), item.sourceTitle(), item.heading(), item.startOffset(), item.endOffset()
        );
    }
}
