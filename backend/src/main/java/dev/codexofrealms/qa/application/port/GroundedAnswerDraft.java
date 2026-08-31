package dev.codexofrealms.qa.application.port;

import dev.codexofrealms.qa.AnswerOutcome;
import java.util.List;

public record GroundedAnswerDraft(AnswerOutcome outcome, List<DraftClaim> claims) {

    public GroundedAnswerDraft {
        claims = claims == null ? List.of() : List.copyOf(claims);
    }

    public static GroundedAnswerDraft insufficient() {
        return new GroundedAnswerDraft(AnswerOutcome.INSUFFICIENT_EVIDENCE, List.of());
    }
}
