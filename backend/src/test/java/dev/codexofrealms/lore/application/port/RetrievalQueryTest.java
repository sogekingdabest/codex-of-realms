package dev.codexofrealms.lore.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.codexofrealms.content.EmbeddingDescriptor;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetrievalQueryTest {

    @Test
    void protectsEmbeddingFromExternalMutation() {
        float[] values = {1.0f, 2.0f};
        RetrievalQuery query = new RetrievalQuery(
            UUID.randomUUID(), UUID.randomUUID(), values,
            new EmbeddingDescriptor("test", "model"), 5
        );

        values[0] = 9.0f;
        float[] returned = query.embedding();
        returned[1] = 9.0f;

        assertThat(query.embedding()).containsExactly(1.0f, 2.0f);
    }

    @Test
    void rejectsEmptyEmbeddingsAndUnboundedLimits() {
        EmbeddingDescriptor descriptor = new EmbeddingDescriptor("test", "model");
        UUID realmId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> new RetrievalQuery(
            realmId, userId, new float[0], descriptor, 5
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetrievalQuery(
            realmId, userId, new float[] {1.0f}, descriptor, 21
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
