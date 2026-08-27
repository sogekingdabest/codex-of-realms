package dev.codexofrealms.content.infrastructure;

import dev.codexofrealms.content.application.RawSourceStorage;
import dev.codexofrealms.content.application.StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;

@Component
class LocalRawSourceStorage implements RawSourceStorage {

    private final Path root;

    LocalRawSourceStorage(StorageProperties properties) {
        this.root = properties.root();
    }

    @Override
    public void write(String key, byte[] content) {
        Path target = resolve(key);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist the raw source.", exception);
        }
    }

    @Override
    public byte[] read(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the raw source.", exception);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete the raw source.", exception);
        }
    }

    private Path resolve(String key) {
        if (key == null || !key.matches("[0-9a-f-]+/[0-9a-f-]+/[0-9a-f-]+\\.(md|txt)")) {
            throw new IllegalArgumentException("Invalid storage key.");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Invalid storage key.");
        return resolved;
    }
}
