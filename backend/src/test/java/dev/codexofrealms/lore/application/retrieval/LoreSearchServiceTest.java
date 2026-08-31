package dev.codexofrealms.lore.application.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.codexofrealms.content.EmbeddingDescriptor;
import dev.codexofrealms.content.TextEmbedding;
import dev.codexofrealms.lore.application.port.LoreRetriever;
import dev.codexofrealms.lore.application.port.RetrievalQuery;
import dev.codexofrealms.realm.RealmAccess;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LoreSearchServiceTest {

    @Test
    void normalizesQuestionAndBuildsRetrievalQueryFromEmbeddingDescriptor() {
        UUID realmId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AtomicReference<RetrievalQuery> captured = new AtomicReference<>();
        RealmAccess realmAccess = new RealmAccess(null, null) {
            @Override
            public void requireMember(UUID requestedRealmId, UUID requestedUserId) {
                assertThat(requestedRealmId).isEqualTo(realmId);
                assertThat(requestedUserId).isEqualTo(userId);
            }
        };
        TextEmbedding embedding = new TextEmbedding() {
            @Override
            public List<float[]> embed(List<String> texts) {
                assertThat(texts).containsExactly("¿Quién es Nara?");
                return List.of(new float[] {1.0f, 2.0f});
            }

            @Override
            public EmbeddingDescriptor descriptor() {
                return new EmbeddingDescriptor("test", "model");
            }
        };
        LoreRetriever retriever = query -> {
            captured.set(query);
            return List.of();
        };
        LoreSearchService service = new LoreSearchService(
            realmAccess, embedding, retriever,
            new RetrievalMetrics(new SimpleMeterRegistry())
        );

        var result = service.retrieve(realmId, userId, "  ¿Quién   es Nara?  ", 7);

        assertThat(result.question()).isEqualTo("¿Quién es Nara?");
        assertThat(result.embeddingProvider()).isEqualTo("test");
        assertThat(result.embeddingModel()).isEqualTo("model");
        assertThat(captured.get().limit()).isEqualTo(7);
        assertThat(captured.get().embedding()).containsExactly(1.0f, 2.0f);
    }

    @Test
    void rejectsInvalidQuestionBeforeEmbedding() {
        AtomicBoolean embedded = new AtomicBoolean();
        TextEmbedding embedding = new TextEmbedding() {
            @Override
            public List<float[]> embed(List<String> texts) {
                embedded.set(true);
                return List.of();
            }

            @Override
            public EmbeddingDescriptor descriptor() {
                return new EmbeddingDescriptor("test", "model");
            }
        };
        RealmAccess realmAccess = new RealmAccess(null, null) {
            @Override
            public void requireMember(UUID realmId, UUID userId) {
            }
        };
        LoreSearchService service = new LoreSearchService(
            realmAccess, embedding, query -> List.of(),
            new RetrievalMetrics(new SimpleMeterRegistry())
        );

        assertThatThrownBy(() -> service.retrieve(
            UUID.randomUUID(), UUID.randomUUID(), "  ", 5
        )).isInstanceOf(IllegalArgumentException.class);
        assertThat(embedded).isFalse();
    }

    @Test
    void rejectsNonMembersBeforeEmbeddingOrRetrieval() {
        UUID realmId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AtomicBoolean embedded = new AtomicBoolean();
        AtomicBoolean retrieved = new AtomicBoolean();
        RealmAccess realmAccess = new RealmAccess(null, null) {
            @Override
            public void requireMember(UUID requestedRealmId, UUID requestedUserId) {
                throw new IllegalStateException("hidden");
            }
        };
        TextEmbedding embedding = new TextEmbedding() {
            @Override
            public List<float[]> embed(List<String> texts) {
                embedded.set(true);
                return List.of(new float[] {1.0f});
            }

            @Override
            public EmbeddingDescriptor descriptor() {
                return new EmbeddingDescriptor("test", "model");
            }
        };
        LoreRetriever retriever = query -> {
            retrieved.set(true);
            return List.of();
        };
        LoreSearchService service = new LoreSearchService(
            realmAccess,
            embedding,
            retriever,
            new RetrievalMetrics(new SimpleMeterRegistry())
        );

        assertThatThrownBy(() -> service.retrieve(realmId, userId, "pregunta", 5))
            .isInstanceOf(IllegalStateException.class);

        org.assertj.core.api.Assertions.assertThat(embedded).isFalse();
        org.assertj.core.api.Assertions.assertThat(retrieved).isFalse();
    }
}
