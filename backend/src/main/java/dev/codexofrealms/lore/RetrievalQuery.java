package dev.codexofrealms.lore;

import dev.codexofrealms.content.EmbeddingDescriptor;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record RetrievalQuery(
    UUID realmId,
    UUID userId,
    float[] embedding,
    EmbeddingDescriptor embeddingDescriptor,
    int limit
) {
    public RetrievalQuery {
        if (realmId == null || userId == null || embeddingDescriptor == null) {
            throw new IllegalArgumentException("Realm, user, and embedding descriptor are required.");
        }
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("A query embedding is required.");
        }
        embedding = embedding.clone();
        double magnitude = 0.0;
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Query embedding contains a non-finite value.");
            }
            magnitude += value * value;
        }
        if (magnitude == 0.0) {
            throw new IllegalArgumentException("Query embedding must have a non-zero magnitude.");
        }
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException("Retrieval limit must be between 1 and 20.");
        }
    }

    @Override
    public float[] embedding() {
        return embedding.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof RetrievalQuery that
            && limit == that.limit
            && realmId.equals(that.realmId)
            && userId.equals(that.userId)
            && Arrays.equals(embedding, that.embedding)
            && embeddingDescriptor.equals(that.embeddingDescriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            realmId, userId, Arrays.hashCode(embedding), embeddingDescriptor, limit
        );
    }

    @Override
    public String toString() {
        return "RetrievalQuery[realmId=" + realmId
            + ", userId=" + userId
            + ", embedding=" + Arrays.toString(embedding)
            + ", embeddingDescriptor=" + embeddingDescriptor
            + ", limit=" + limit + "]";
    }
}
