package dev.codexofrealms.qa.application.answering;

import dev.codexofrealms.lore.RetrievedEvidence;
import dev.codexofrealms.qa.*;
import dev.codexofrealms.qa.application.port.GroundedAnswerDraft;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class AnswerValidator {
    private final AnsweringProperties properties;
    AnswerValidator(AnsweringProperties properties) { this.properties = properties; }

    LoreAnswer validate(UUID realmId, GroundedAnswerDraft draft, List<RetrievedEvidence> visibleEvidence,
                        AnswerProvenance provenance) {
        if (draft == null || draft.outcome() == null) return invalid(provenance);
        if (draft.outcome() == AnswerOutcome.INSUFFICIENT_EVIDENCE) {
            return draft.passageIds().isEmpty()
                ? LoreAnswer.insufficient(provenance, AnswerFailureReason.NO_EVIDENCE) : invalid(provenance);
        }
        if (draft.passageIds().isEmpty() || draft.passageIds().size() > properties.maxExcerpts()
            || new HashSet<>(draft.passageIds()).size() != draft.passageIds().size()) return invalid(provenance);
        Map<String, RetrievedEvidence> available = new HashMap<>();
        visibleEvidence.forEach(item -> available.put(item.passageId(), item));
        List<Citation> citations = new ArrayList<>();
        List<AnswerExcerpt> excerpts = new ArrayList<>();
        List<String> rendered = new ArrayList<>();
        int length = 0;
        for (String id : draft.passageIds()) {
            RetrievedEvidence item = available.get(id);
            if (item == null || item.content().isBlank()) return invalid(provenance);
            length += item.content().length();
            if (length > properties.maxAnswerCharacters()) return invalid(provenance);
            citations.add(new Citation(item.rank(), realmId, item.chunkId(), item.sourceDocumentId(),
                item.documentVersionId(), item.versionNumber(), item.sourceTitle(), item.heading(),
                item.startOffset(), item.endOffset()));
            excerpts.add(new AnswerExcerpt(item.content(), item.rank()));
            rendered.add(item.content() + " [" + item.rank() + "]");
        }
        return new LoreAnswer(AnswerOutcome.ANSWERED, String.join("\n\n", rendered), citations,
            provenance, null, "EXTRACTIVE", excerpts);
    }

    private static LoreAnswer invalid(AnswerProvenance provenance) {
        return LoreAnswer.insufficient(provenance, AnswerFailureReason.VALIDATION_FAILED);
    }
}
