package dev.codexofrealms.lore;

import java.util.UUID;

public interface LoreSearch {

    RetrievalResult retrieve(UUID realmId, UUID userId, String question, int limit);
}
