package dev.codexofrealms.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeCapabilitiesServiceTest {

    @Test
    void reportsProvidersAsNotConfiguredWithoutProbingTheRuntime() {
        RuntimeCapabilitiesService service = new RuntimeCapabilitiesService(
            () -> {
                throw new AssertionError("The runtime must not be probed when Ollama is disabled.");
            },
            "none",
            "",
            "none",
            ""
        );

        RuntimeCapabilities capabilities = service.capabilities();

        assertThat(capabilities.chat().status()).isEqualTo("NOT_CONFIGURED");
        assertThat(capabilities.embedding().status()).isEqualTo("NOT_CONFIGURED");
    }

    @Test
    void distinguishesReadyAndMissingModels() {
        RuntimeCapabilitiesService service = new RuntimeCapabilitiesService(
            () -> Optional.of(List.of("bge-m3", "qwen3:4b")),
            "ollama",
            "qwen3:4b",
            "ollama",
            "missing-embedding"
        );

        RuntimeCapabilities capabilities = service.capabilities();

        assertThat(capabilities.chat().available()).isTrue();
        assertThat(capabilities.chat().status()).isEqualTo("READY");
        assertThat(capabilities.embedding().available()).isFalse();
        assertThat(capabilities.embedding().status()).isEqualTo("MODEL_MISSING");
        assertThat(capabilities.chat().installedModels()).containsExactly("bge-m3", "qwen3:4b");
    }

    @Test
    void reportsAnUnavailableRuntimeWithoutLeakingAdapterFailures() {
        RuntimeCapabilitiesService service = new RuntimeCapabilitiesService(
            Optional::empty,
            "ollama",
            "qwen3:4b",
            "ollama",
            "bge-m3"
        );

        RuntimeCapabilities capabilities = service.capabilities();

        assertThat(capabilities.chat().status()).isEqualTo("RUNTIME_UNAVAILABLE");
        assertThat(capabilities.embedding().status()).isEqualTo("RUNTIME_UNAVAILABLE");
        assertThat(capabilities.chat().installedModels()).isEmpty();
    }
}
