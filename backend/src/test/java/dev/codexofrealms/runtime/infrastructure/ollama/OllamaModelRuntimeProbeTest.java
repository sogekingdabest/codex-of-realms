package dev.codexofrealms.runtime.infrastructure.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.ObjectProvider;

class OllamaModelRuntimeProbeTest {

    @Test
    void reportsTheRuntimeAsUnavailableWhenNoClientExists() {
        ObjectProvider<OllamaApi> provider = provider(null);

        assertThat(new OllamaModelRuntimeProbe(provider).installedModels()).isEmpty();
    }

    @Test
    void hidesClientFailuresAsAnUnavailableRuntime() {
        OllamaApi api = mock(OllamaApi.class);
        when(api.listModels()).thenThrow(new IllegalStateException("connection refused"));

        assertThat(new OllamaModelRuntimeProbe(provider(api)).installedModels()).isEmpty();
    }

    @Test
    void normalizesDeduplicatesAndSortsInstalledModelNames() {
        OllamaApi api = mock(OllamaApi.class);
        when(api.listModels()).thenReturn(new OllamaApi.ListModelResponse(List.of(
            model(" qwen3:4b ", null),
            model(null, "bge-m3"),
            model("bge-m3", null),
            model(" ", null),
            model(null, null)
        )));

        List<String> installed = new OllamaModelRuntimeProbe(provider(api))
            .installedModels()
            .orElseThrow();

        assertThat(installed).containsExactly("bge-m3", "qwen3:4b");
        assertThatThrownBy(() -> installed.add("other"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private static OllamaApi.Model model(String name, String model) {
        return new OllamaApi.Model(name, model, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<OllamaApi> provider(OllamaApi api) {
        ObjectProvider<OllamaApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(api);
        return provider;
    }
}
