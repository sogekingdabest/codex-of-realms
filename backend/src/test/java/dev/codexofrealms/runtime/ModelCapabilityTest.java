package dev.codexofrealms.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelCapabilityTest {

    @Test
    void copiesInstalledModelsDefensively() {
        List<String> installed = new ArrayList<>(List.of("qwen3:4b"));

        ModelCapability capability = new ModelCapability(
            "ollama",
            "qwen3:4b",
            true,
            ModelCapabilityStatus.READY,
            installed
        );
        installed.add("bge-m3");

        assertThat(capability.installedModels()).containsExactly("qwen3:4b");
        assertThatThrownBy(() -> capability.installedModels().add("other"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsAvailabilityThatDoesNotMatchTheStatus() {
        assertThatThrownBy(() -> new ModelCapability(
            "ollama",
            "qwen3:4b",
            true,
            ModelCapabilityStatus.MODEL_MISSING,
            List.of()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("READY");
    }
}
