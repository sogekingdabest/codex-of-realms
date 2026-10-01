package dev.codexofrealms.runtime.application.port;

/**
 * Loads configured models into memory so that the first request does not pay their load time.
 */
public interface ModelPreloader {

    void loadEmbedding(String model);

    void loadChat(String model, String keepAlive);
}
