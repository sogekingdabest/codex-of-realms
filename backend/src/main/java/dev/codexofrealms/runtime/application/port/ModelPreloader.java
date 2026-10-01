package dev.codexofrealms.runtime.application.port;

/**
 * Loads configured models into memory so that the first request does not pay their load time.
 */
public interface ModelPreloader {

    void loadEmbedding(String model);

    /**
     * The context size must match the one used for answers: Ollama reloads a model whose
     * runner options differ from the loaded copy.
     */
    void loadChat(String model, String keepAlive, int contextSize);
}
