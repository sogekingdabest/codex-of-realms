package dev.codexofrealms.runtime.infrastructure;

import dev.codexofrealms.runtime.application.ModelRuntimeProbe;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class OllamaModelRuntimeProbe implements ModelRuntimeProbe {

    private final ObjectProvider<OllamaApi> ollamaApi;

    public OllamaModelRuntimeProbe(ObjectProvider<OllamaApi> ollamaApi) {
        this.ollamaApi = ollamaApi;
    }

    @Override
    public Optional<List<String>> installedModels() {
        OllamaApi api = ollamaApi.getIfAvailable();
        if (api == null) return Optional.empty();
        try {
            return Optional.of(api.listModels().models().stream()
                .map(model -> model.name() == null ? model.model() : model.name())
                .sorted()
                .toList());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
