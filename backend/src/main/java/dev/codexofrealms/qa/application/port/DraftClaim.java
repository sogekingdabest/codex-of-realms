package dev.codexofrealms.qa.application.port;

import java.util.List;

public record DraftClaim(String text, List<Integer> citations) {

    public DraftClaim {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
