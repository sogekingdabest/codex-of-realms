package dev.codexofrealms.runtime.infrastructure.ollama;

import dev.codexofrealms.runtime.application.port.ModelRuntimeProbe;
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
        if (api == null) {
            return Optional.empty();
        }
        try {
            List<String> installed = api.listModels().models().stream()
                .map(model -> model.name() == null ? model.model() : model.name())
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
            return Optional.of(installed);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
