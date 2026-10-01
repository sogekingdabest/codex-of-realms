package dev.codexofrealms.realm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codexofrealms.realm.domain.AccessClassification;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AccessVisibilityTest {

    @Test
    void publicVisibilityMirrorsTheStoredClassifications() {
        assertThat(Arrays.stream(AccessVisibility.values()).map(Enum::name))
            .containsExactlyElementsOf(Arrays.stream(AccessClassification.values()).map(Enum::name).toList());
    }
}
