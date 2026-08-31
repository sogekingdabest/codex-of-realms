package dev.codexofrealms.runtime.application;

import java.util.List;

public record ModelCapability(
    String provider,
    String model,
    boolean available,
    String status,
    List<String> installedModels
) {
}
