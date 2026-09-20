package dev.codexofrealms.runtime.application.capabilities;

import dev.codexofrealms.runtime.ModelCapability;
import dev.codexofrealms.runtime.ModelCapabilityStatus;
import dev.codexofrealms.runtime.RuntimeCapabilities;
import dev.codexofrealms.runtime.application.capabilities.RuntimeModelConfiguration.ConfiguredModel;
import dev.codexofrealms.runtime.application.port.ModelRuntimeProbe;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class RuntimeCapabilitiesService {

    private final ModelRuntimeProbe runtimeProbe;
    private final RuntimeModelConfiguration configuration;

    public RuntimeCapabilitiesService(
        ModelRuntimeProbe runtimeProbe,
        RuntimeModelConfiguration configuration
    ) {
        this.runtimeProbe = runtimeProbe;
        this.configuration = configuration;
    }

    public RuntimeCapabilities capabilities() {
        if (!configuration.chat().usesOllama() && !configuration.embedding().usesOllama()) {
            return evaluate(Optional.empty());
        }
        return evaluate(runtimeProbe.installedModels());
    }

    private RuntimeCapabilities evaluate(Optional<List<String>> installedModels) {
        return new RuntimeCapabilities(
            capability(configuration.chat(), installedModels),
            capability(configuration.embedding(), installedModels)
        );
    }

    private static ModelCapability capability(
        ConfiguredModel configuredModel,
        Optional<List<String>> installedModels
    ) {
        if (!configuredModel.usesOllama()) {
            return capability(configuredModel, ModelCapabilityStatus.NOT_CONFIGURED, List.of());
        }
        if (installedModels.isEmpty()) {
            return capability(configuredModel, ModelCapabilityStatus.RUNTIME_UNAVAILABLE, List.of());
        }

        List<String> installed = installedModels.orElseThrow();
        ModelCapabilityStatus status = installed.stream()
            .anyMatch(model -> withoutDefaultTag(model).equals(withoutDefaultTag(configuredModel.model())))
            ? ModelCapabilityStatus.READY
            : ModelCapabilityStatus.MODEL_MISSING;
        return capability(configuredModel, status, installed);
    }

    private static String withoutDefaultTag(String model) {
        return model.endsWith(":latest") ? model.substring(0, model.length() - ":latest".length()) : model;
    }

    private static ModelCapability capability(
        ConfiguredModel configuredModel,
        ModelCapabilityStatus status,
        List<String> installedModels
    ) {
        return new ModelCapability(
            configuredModel.provider(),
            configuredModel.model(),
            status == ModelCapabilityStatus.READY,
            status,
            installedModels
        );
    }
}
