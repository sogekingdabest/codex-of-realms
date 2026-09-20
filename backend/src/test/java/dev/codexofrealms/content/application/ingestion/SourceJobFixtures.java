package dev.codexofrealms.content.application.ingestion;

import dev.codexofrealms.content.application.port.*;

import java.time.Instant;
import java.util.*;

class SourceJobFixtures {
    static final IngestionProperties CONFIG =
            new IngestionProperties(20_000, 200, 20, 1, "test", "model");
    static final UUID REALM = UUID.randomUUID(),
            USER = UUID.randomUUID(),
            POLICY = UUID.randomUUID();
    static final byte[] BYTES =
            ("# Crónica\n\n" + "Nara no entregó las 37 monedas el 2 de mayo. ".repeat(20))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);

    static SourceJob job(SourceJobState state) {
        UUID doc = UUID.randomUUID(), version = UUID.randomUUID(), id = UUID.randomUUID();
        SourceVersion source =
                new SourceVersion(
                        doc,
                        version,
                        1,
                        "Crónica",
                        "cronica.md",
                        "text/markdown",
                        "es",
                        SourceIngestionService.sha256(BYTES),
                        REALM + "/" + doc + "/" + version + ".md",
                        POLICY,
                        "fingerprint");
        SourceJobView view =
                new SourceJobView(
                        id,
                        doc,
                        version,
                        1,
                        "Crónica",
                        "cronica.md",
                        POLICY,
                        state,
                        1,
                        0,
                        0,
                        null,
                        false,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        List.of());
        return new SourceJob(
                view,
                REALM,
                USER,
                "hash",
                CONFIG.toString(),
                source,
                state == SourceJobState.RUNNING ? UUID.randomUUID() : null);
    }
}
