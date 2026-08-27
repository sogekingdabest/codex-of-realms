package dev.codexofrealms.lore.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.codexofrealms.content.EmbeddingDescriptor;
import dev.codexofrealms.content.TextEmbedding;
import dev.codexofrealms.lore.LoreRetriever;
import dev.codexofrealms.realm.RealmAccess;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class LoreSearchServiceTest {

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
