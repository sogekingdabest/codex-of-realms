package dev.codexofrealms.runtime;

import java.util.List;
import java.util.Objects;

public record ModelCapability(
    String provider,
    String model,
    boolean available,
    ModelCapabilityStatus status,
    List<String> installedModels
) {
    public ModelCapability {
        provider = Objects.requireNonNull(provider, "Provider is required.");
        model = Objects.requireNonNull(model, "Model is required.");
        status = Objects.requireNonNull(status, "Capability status is required.");
        installedModels = List.copyOf(Objects.requireNonNull(installedModels, "Installed models are required."));
        if (available != (status == ModelCapabilityStatus.READY)) {
            throw new IllegalArgumentException("A model capability is available only when its status is READY.");
        }
    }
}
