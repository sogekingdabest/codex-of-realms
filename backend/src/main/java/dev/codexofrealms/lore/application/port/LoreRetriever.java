package dev.codexofrealms.lore.application.port;

import java.util.List;

import dev.codexofrealms.lore.RetrievedEvidence;

public interface LoreRetriever {

    List<RetrievedEvidence> retrieve(RetrievalQuery query);
}
