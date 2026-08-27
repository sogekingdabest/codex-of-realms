package dev.codexofrealms.content.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.codexofrealms.content.application.StorageProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRawSourceStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsOnlyGeneratedScopedKeysAndDeletesIdempotently() {
        LocalRawSourceStorage storage = new LocalRawSourceStorage(
            new StorageProperties(temporaryDirectory)
        );
        String key = "00000000-0000-0000-0000-000000000001/"
            + "00000000-0000-0000-0000-000000000002/"
            + "00000000-0000-0000-0000-000000000003.md";
        byte[] content = "Lumbrevela".getBytes(StandardCharsets.UTF_8);

        storage.write(key, content);
        assertThat(storage.read(key)).isEqualTo(content);
        storage.delete(key);
        storage.delete(key);

        assertThatThrownBy(() -> storage.write("../../secret.txt", content))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
