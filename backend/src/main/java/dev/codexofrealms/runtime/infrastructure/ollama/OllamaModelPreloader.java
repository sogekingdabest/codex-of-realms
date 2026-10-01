package dev.codexofrealms.runtime.infrastructure.ollama;

import dev.codexofrealms.runtime.application.port.ModelPreloader;
import java.util.List;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class OllamaModelPreloader implements ModelPreloader {

    private static final String WARM_UP_INPUT = "Codex of Realms";

    private final ObjectProvider<OllamaApi> ollamaApi;

    public OllamaModelPreloader(ObjectProvider<OllamaApi> ollamaApi) {
        this.ollamaApi = ollamaApi;
    }

    @Override
    public void loadEmbedding(String model) {
        api().embed(new OllamaApi.EmbeddingsRequest(model, List.of(WARM_UP_INPUT), null, null, null, null));
    }

    @Override
    public void loadChat(String model, String keepAlive) {
        // Ollama loads a chat model without generating any text when the message list is empty.
        api().chat(OllamaApi.ChatRequest.builder(model).messages(List.of()).keepAlive(keepAlive).build());
    }

    private OllamaApi api() {
        OllamaApi api = ollamaApi.getIfAvailable();
        if (api == null) {
            throw new IllegalStateException("No Ollama client is configured.");
        }
        return api;
    }
}
