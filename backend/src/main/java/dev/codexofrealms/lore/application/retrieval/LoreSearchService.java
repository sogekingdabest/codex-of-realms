package dev.codexofrealms.lore.application.retrieval;

import dev.codexofrealms.content.EmbeddingDescriptor;
import dev.codexofrealms.content.TextEmbedding;
import dev.codexofrealms.lore.LoreSearch;
import dev.codexofrealms.lore.RetrievalResult;
import dev.codexofrealms.lore.RetrievedEvidence;
import dev.codexofrealms.lore.application.port.LoreRetriever;
import dev.codexofrealms.lore.application.port.RetrievalQuery;
import dev.codexofrealms.realm.RealmAccess;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LoreSearchService implements LoreSearch {

    private final RealmAccess realmAccess;
    private final TextEmbedding embedding;
    private final LoreRetriever retriever;
    private final RetrievalMetrics metrics;

    LoreSearchService(
        RealmAccess realmAccess,
        TextEmbedding embedding,
        LoreRetriever retriever,
        RetrievalMetrics metrics
    ) {
        this.realmAccess = realmAccess;
        this.embedding = embedding;
        this.retriever = retriever;
        this.metrics = metrics;
    }

    @Override
    public RetrievalResult retrieve(
        UUID realmId,
        UUID userId,
        String requestedQuestion,
        int limit
    ) {
        Timer.Sample sample = metrics.start();
        try {
            realmAccess.requireMember(realmId, userId);
            String question = normalizeQuestion(requestedQuestion);
            EmbeddingDescriptor descriptor = embedding.descriptor();
            float[] queryEmbedding = embedding.embed(question);
            List<RetrievedEvidence> evidence = retriever.retrieve(new RetrievalQuery(
                realmId, userId, queryEmbedding, descriptor, limit
            ));
            metrics.success(sample, evidence);
            return new RetrievalResult(question, descriptor.provider(), descriptor.model(), evidence);
        } catch (RuntimeException exception) {
            metrics.failure(sample);
            throw exception;
        }
    }

    private static String normalizeQuestion(String requestedQuestion) {
        if (requestedQuestion == null) {
            throw new IllegalArgumentException("Question is required.");
        }
        String question = requestedQuestion.strip().replaceAll("\\s+", " ");
        if (question.isEmpty() || question.length() > 1000) {
            throw new IllegalArgumentException("Question must contain between 1 and 1000 characters.");
        }
        if (question.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Question contains unsupported control characters.");
        }
        return question;
    }
}
