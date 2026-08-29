package dev.codexofrealms.lore.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoreRelationTest {

    @Test
    void normalizesAReadableRelationTypeToAControlledIdentifier() {
        LoreRelation relation = new LoreRelation(
            UUID.randomUUID(), UUID.randomUUID(), " protegió a ", "  Juró lealtad.  "
        );

        assertThat(relation.relationType()).isEqualTo("PROTEGIO_A");
        assertThat(relation.description()).isEqualTo("Juró lealtad.");
    }

    @Test
    void rejectsSelfRelationsAndEmptyTypes() {
        UUID entityId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        assertThatThrownBy(() -> new LoreRelation(entityId, entityId, "ALIADO_DE", ""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoreRelation(
            sourceId, targetId, "---", ""
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
