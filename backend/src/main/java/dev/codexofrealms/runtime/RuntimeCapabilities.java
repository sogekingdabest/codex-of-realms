package dev.codexofrealms.runtime;

import java.util.Objects;

public record RuntimeCapabilities(ModelCapability chat, ModelCapability embedding) {
    public RuntimeCapabilities {
        chat = Objects.requireNonNull(chat, "Chat capability is required.");
        embedding = Objects.requireNonNull(embedding, "Embedding capability is required.");
    }
}
