package dev.codexofrealms.runtime.application.capabilities;

import static dev.codexofrealms.runtime.ModelCapabilityStatus.MODEL_MISSING;
import static dev.codexofrealms.runtime.ModelCapabilityStatus.NOT_CONFIGURED;
import static dev.codexofrealms.runtime.ModelCapabilityStatus.READY;
import static dev.codexofrealms.runtime.ModelCapabilityStatus.RUNTIME_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.codexofrealms.runtime.RuntimeCapabilities;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuntimeCapabilitiesServiceTest {

    @ParameterizedTest
    @CsvSource({
        "bge-m3, bge-m3:latest, READY",
        "bge-m3:latest, bge-m3, READY",
        "registry.local:5000/team/model, registry.local:5000/team/model:latest, READY",
        "qwen3:4b, qwen3:latest, MODEL_MISSING",
        "qwen3, qwen3:4b, MODEL_MISSING"
    })
    void recognizesTheDefaultTagWithoutConfusingExplicitVersions(
        String configured, String installed, dev.codexofrealms.runtime.ModelCapabilityStatus expected
    ) {
        RuntimeCapabilities capabilities = service(
            () -> Optional.of(List.of(installed)), "none", "", "ollama", configured
        ).capabilities();

        assertThat(capabilities.embedding().status()).isEqualTo(expected);
        assertThat(capabilities.embedding().model()).isEqualTo(configured);
        assertThat(capabilities.embedding().installedModels()).containsExactly(installed);
    }

    @Test
    void reportsProvidersAsNotConfiguredWithoutProbingTheRuntime() {
        RuntimeCapabilitiesService service = service(
            () -> {
                throw new AssertionError("The runtime must not be probed when Ollama is disabled.");
            },
            "none",
            "",
            "unsupported",
            "some-model"
        );

        RuntimeCapabilities capabilities = service.capabilities();

        assertThat(capabilities.chat().status()).isEqualTo(NOT_CONFIGURED);
        assertThat(capabilities.embedding().status()).isEqualTo(NOT_CONFIGURED);
        assertThat(capabilities.embedding().provider()).isEqualTo("unsupported");
        assertThat(capabilities.embedding().installedModels()).isEmpty();
    }

    @Test
    void distinguishesReadyAndMissingOllamaModels() {
        RuntimeCapabilitiesService service = service(
            () -> Optional.of(List.of("bge-m3", "qwen3:4b")),
            "ollama",
            "qwen3:4b",
            "ollama",
            "missing-embedding"
        );

        RuntimeCapabilities capabilities = service.capabilities();

        assertThat(capabilities.chat().available()).isTrue();
        assertThat(capabilities.chat().status()).isEqualTo(READY);
        assertThat(capabilities.embedding().available()).isFalse();
        assertThat(capabilities.embedding().status()).isEqualTo(MODEL_MISSING);
        assertThat(capabilities.chat().installedModels()).containsExactly("bge-m3", "qwen3:4b");
    }

    @Test
    void doesNotReportANonOllamaChatProviderAsReadyInAMixedConfiguration() {
        RuntimeCapabilitiesService service = service(
            () -> Optional.of(List.of("same-model")),
            "none",
            "same-model",
            "ollama",
            "same-model"
        );

        RuntimeCapabilities capabilities = service.capabilities();

        assertThat(capabilities.chat().status()).isEqualTo(NOT_CONFIGURED);
        assertThat(capabilities.chat().available()).isFalse();
        assertThat(capabilities.chat().installedModels()).isEmpty();
        assertThat(capabilities.embedding().status()).isEqualTo(READY);
    }

    @Test
    void doesNotReportANonOllamaEmbeddingProviderAsReadyInAMixedConfiguration() {
        RuntimeCapabilitiesService service = service(
            () -> Optional.of(List.of("same-model")),
            "ollama",
            "same-model",
            "none",
            "same-model"
        );

        RuntimeCapabilities capabilities = service.capabilities();

        assertThat(capabilities.chat().status()).isEqualTo(READY);
        assertThat(capabilities.embedding().status()).isEqualTo(NOT_CONFIGURED);
        assertThat(capabilities.embedding().available()).isFalse();
        assertThat(capabilities.embedding().installedModels()).isEmpty();
    }

    @Test
    void reportsUnavailableOnlyForTheConfiguredOllamaCapability() {
        RuntimeCapabilitiesService service = service(
            Optional::empty,
            "ollama",
            "qwen3:4b",
            "none",
            "bge-m3"
        );

        RuntimeCapabilities capabilities = service.capabilities();

        assertThat(capabilities.chat().status()).isEqualTo(RUNTIME_UNAVAILABLE);
        assertThat(capabilities.embedding().status()).isEqualTo(NOT_CONFIGURED);
        assertThat(capabilities.chat().installedModels()).isEmpty();
    }

    @Test
    void treatsABlankOllamaModelAsNotConfiguredWithoutProbing() {
        RuntimeCapabilitiesService service = service(
            () -> {
                throw new AssertionError("An incomplete Ollama configuration must not be probed.");
            },
            " OLLAMA ",
            "  ",
            "none",
            ""
        );

        RuntimeCapabilities capabilities = service.capabilities();

        assertThat(capabilities.chat().provider()).isEqualTo("ollama");
        assertThat(capabilities.chat().model()).isEmpty();
        assertThat(capabilities.chat().status()).isEqualTo(NOT_CONFIGURED);
    }

    private static RuntimeCapabilitiesService service(
        dev.codexofrealms.runtime.application.port.ModelRuntimeProbe runtimeProbe,
        String chatProvider,
        String chatModel,
        String embeddingProvider,
        String embeddingModel
    ) {
        return new RuntimeCapabilitiesService(
            runtimeProbe,
            RuntimeModelConfiguration.of(chatProvider, chatModel, embeddingProvider, embeddingModel)
        );
    }
}
