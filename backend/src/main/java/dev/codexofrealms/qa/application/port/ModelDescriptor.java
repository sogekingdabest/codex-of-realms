package dev.codexofrealms.qa.application.port;

public record ModelDescriptor(String provider, String model) {

    public ModelDescriptor {
        if (provider == null || provider.isBlank() || model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model provider and name are required.");
        }
    }
}
