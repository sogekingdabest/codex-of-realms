package dev.codexofrealms.content.application;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("codex.storage")
public record StorageProperties(Path root) {
    public StorageProperties {
        root = root.toAbsolutePath().normalize();
    }
}
