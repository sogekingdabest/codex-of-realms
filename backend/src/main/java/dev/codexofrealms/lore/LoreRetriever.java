package dev.codexofrealms.lore;

import java.util.List;

public interface LoreRetriever {

    List<RetrievedEvidence> retrieve(RetrievalQuery query);
}
