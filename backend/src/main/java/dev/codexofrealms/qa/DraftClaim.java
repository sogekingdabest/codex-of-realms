package dev.codexofrealms.qa;

import java.util.List;

public record DraftClaim(String text, List<Integer> citations) {

    public DraftClaim {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
