package dev.codexofrealms;

import java.util.List;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/capabilities")
class RuntimeCapabilitiesController {

    private final ObjectProvider<OllamaApi> ollamaApi;
    private final String chatProvider;
    private final String chatModel;
    private final String embeddingProvider;
    private final String embeddingModel;

    RuntimeCapabilitiesController(
        ObjectProvider<OllamaApi> ollamaApi,
        @Value("${spring.ai.model.chat:none}") String chatProvider,
        @Value("${spring.ai.ollama.chat.model:}") String chatModel,
        @Value("${spring.ai.model.embedding:none}") String embeddingProvider,
        @Value("${spring.ai.ollama.embedding.model:}") String embeddingModel
    ) {
        this.ollamaApi = ollamaApi;
        this.chatProvider = chatProvider;
        this.chatModel = chatModel;
        this.embeddingProvider = embeddingProvider;
        this.embeddingModel = embeddingModel;
    }

    @GetMapping
    RuntimeCapabilities capabilities() {
        if (!"ollama".equals(chatProvider) && !"ollama".equals(embeddingProvider)) {
            return new RuntimeCapabilities(
                capability(chatProvider, chatModel, false, "NOT_CONFIGURED", List.of()),
                capability(embeddingProvider, embeddingModel, false, "NOT_CONFIGURED", List.of())
            );
        }

        OllamaApi api = ollamaApi.getIfAvailable();
        if (api == null) {
            return unavailable("RUNTIME_UNAVAILABLE", List.of());
        }
        try {
            List<String> installed = api.listModels().models().stream()
                .map(model -> model.name() == null ? model.model() : model.name())
                .sorted()
                .toList();
            return new RuntimeCapabilities(
                capability(chatProvider, chatModel, installed.contains(chatModel),
                    installed.contains(chatModel) ? "READY" : "MODEL_MISSING", installed),
                capability(embeddingProvider, embeddingModel, installed.contains(embeddingModel),
                    installed.contains(embeddingModel) ? "READY" : "MODEL_MISSING", installed)
            );
        } catch (RuntimeException exception) {
            return unavailable("RUNTIME_UNAVAILABLE", List.of());
        }
    }

    private RuntimeCapabilities unavailable(String status, List<String> installed) {
        return new RuntimeCapabilities(
            capability(chatProvider, chatModel, false, status, installed),
            capability(embeddingProvider, embeddingModel, false, status, installed)
        );
    }

    private static ModelCapability capability(
        String provider,
        String model,
        boolean available,
        String status,
        List<String> installed
    ) {
        return new ModelCapability(provider, model, available, status, installed);
    }

    record RuntimeCapabilities(ModelCapability chat, ModelCapability embedding) {
    }

    record ModelCapability(
        String provider,
        String model,
        boolean available,
        String status,
        List<String> installedModels
    ) {
    }
}
