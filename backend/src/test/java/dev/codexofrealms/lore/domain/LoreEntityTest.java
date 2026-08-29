package dev.codexofrealms.lore.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class LoreEntityTest {

    @Test
    void normalizesNamesAndKeepsDistinctAliasesInOrder() {
        LoreEntity entity = new LoreEntity(
            EntityType.CHARACTER,
            "  Nara   Vey  ",
            List.of(" La Cartógrafa ", "la cartógrafa", "NARA VEY", "Capitana"),
            "  Custodia los mapas.  "
        );

        assertThat(entity.displayName()).isEqualTo("Nara Vey");
        assertThat(entity.aliases()).containsExactly("La Cartógrafa", "Capitana");
        assertThat(entity.description()).isEqualTo("Custodia los mapas.");
    }

    @Test
    void rejectsMissingOrOversizedValues() {
        List<String> noAliases = List.of();
        List<String> oversizedAlias = List.of("x".repeat(121));
        assertThatThrownBy(() -> new LoreEntity(EntityType.PLACE, " ", noAliases, ""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoreEntity(
            EntityType.PLACE, "Aguja", oversizedAlias, ""
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
