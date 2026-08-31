package dev.codexofrealms.runtime.application;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RuntimeCapabilitiesService {

    private final ModelRuntimeProbe runtimeProbe;
    private final String chatProvider;
    private final String chatModel;
    private final String embeddingProvider;
    private final String embeddingModel;

    public RuntimeCapabilitiesService(
        ModelRuntimeProbe runtimeProbe,
        @Value("${spring.ai.model.chat:none}") String chatProvider,
        @Value("${spring.ai.ollama.chat.model:}") String chatModel,
        @Value("${spring.ai.model.embedding:none}") String embeddingProvider,
        @Value("${spring.ai.ollama.embedding.model:}") String embeddingModel
    ) {
        this.runtimeProbe = runtimeProbe;
        this.chatProvider = chatProvider;
        this.chatModel = chatModel;
        this.embeddingProvider = embeddingProvider;
        this.embeddingModel = embeddingModel;
    }

    public RuntimeCapabilities capabilities() {
        if (!"ollama".equals(chatProvider) && !"ollama".equals(embeddingProvider)) {
            return new RuntimeCapabilities(
                capability(chatProvider, chatModel, false, "NOT_CONFIGURED", List.of()),
                capability(embeddingProvider, embeddingModel, false, "NOT_CONFIGURED", List.of())
            );
        }

        return runtimeProbe.installedModels()
            .map(this::availableCapabilities)
            .orElseGet(() -> unavailable("RUNTIME_UNAVAILABLE", List.of()));
    }

    private RuntimeCapabilities availableCapabilities(List<String> installed) {
        return new RuntimeCapabilities(
            capability(
                chatProvider,
                chatModel,
                installed.contains(chatModel),
                installed.contains(chatModel) ? "READY" : "MODEL_MISSING",
                installed
            ),
            capability(
                embeddingProvider,
                embeddingModel,
                installed.contains(embeddingModel),
                installed.contains(embeddingModel) ? "READY" : "MODEL_MISSING",
                installed
            )
        );
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
}
