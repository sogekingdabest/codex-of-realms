package dev.codexofrealms.content.infrastructure;

import dev.codexofrealms.content.application.port.RawSourceStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class LocalRawSourceStorage implements RawSourceStorage {

    private static final Pattern STORAGE_KEY = Pattern.compile(
        "[0-9a-f-]++/[0-9a-f-]++/[0-9a-f-]++\\.(?:md|txt)"
    );

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
            moveIntoPlace(temporary, target);
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
        if (key == null || !STORAGE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key.");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Invalid storage key.");
        return resolved;
    }

    private static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
