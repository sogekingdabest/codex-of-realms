package dev.codexofrealms.lore;

import java.util.List;
import java.util.UUID;

public interface LoreEvidence {
    List<RetrievedEvidence> passages(UUID realmId, UUID userId, String question, List<RetrievedEvidence> chunks, int limit);
    boolean stillVisible(UUID realmId, UUID userId, List<RetrievedEvidence> passages);
}
