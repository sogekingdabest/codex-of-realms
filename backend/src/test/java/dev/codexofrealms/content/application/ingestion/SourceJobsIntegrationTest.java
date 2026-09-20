package dev.codexofrealms.content.application.ingestion;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.codexofrealms.content.*;
import dev.codexofrealms.content.application.port.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootTest(
        properties = {
            "codex.storage.root=target/job-integration-sources",
            "codex.ingestion.embedding-provider=test",
            "codex.ingestion.embedding-model=jobs-v1",
            "codex.ingestion.poll-milliseconds=3600000"
        })
@AutoConfigureMockMvc
@Testcontainers
@Import(SourceJobsIntegrationTest.Models.class)
class SourceJobsIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.6-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"));

    @Autowired SourceIngestionService service;
    @Autowired SourceJobRepository jobs;
    @Autowired SourceJobWorker worker;
    @Autowired SourceJobCoordinator coordinator;
    @Autowired RawSourceStorage storage;
    @Autowired JdbcClient jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AtomicBoolean modelFails;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    UUID realm, user, policy;

    @BeforeEach
    void setup() throws Exception {
        jdbc.sql("TRUNCATE codex_user, realm CASCADE").update();
        modelFails.set(false);
        String response =
                mvc.perform(
                                post("/api/v1/realms")
                                        .with(identity("owner"))
                                        .contentType("application/json")
                                        .content("{\"name\":\"Jobs realm\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        realm = UUID.fromString(json.readTree(response).get("id").asString());
        user =
                jdbc.sql("SELECT id FROM codex_user WHERE subject='owner'")
                        .query(UUID.class)
                        .single();
        policy =
                jdbc.sql(
                                "SELECT id FROM access_policy WHERE realm_id=:realm AND"
                                    + " classification='PUBLIC'")
                        .param("realm", realm)
                        .query(UUID.class)
                        .single();
    }

    SourceSubmission submit(String key, String text) {
        return service.create(
                realm,
                user,
                "Crónica",
                policy,
                text.getBytes(StandardCharsets.UTF_8),
                "cronica.md",
                "text/markdown",
                key);
    }

    SourceJob execute() {
        SourceJob job = jobs.claim().orElseThrow();
        worker.process(job);
        return job;
    }

    @Test
    void sameKeyReturnsSameOperationAndDifferentPayloadConflicts() {
        var first = submit("one", "Nara conserva 37 monedas.");
        var duplicate = submit("one", "Nara conserva 37 monedas.");
        assertThat(duplicate.job().id()).isEqualTo(first.job().id());
        assertThatThrownBy(() -> submit("one", "Nara conserva 38 monedas."))
                .isInstanceOf(SourceJobException.class);
        assertThat(jdbc.sql("SELECT count(*) FROM source_job").query(Integer.class).single())
                .isEqualTo(1);
    }

    @Test
    void twoWorkersClaimOnceAndExpiredLeaseCannotWriteOrActivate() throws Exception {
        var submitted = submit("claim", "Nara conserva 37 monedas.");
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Optional<SourceJob>> claim =
                    () -> {
                        start.await();
                        return jobs.claim();
                    };
            var one = executor.submit(claim);
            var two = executor.submit(claim);
            start.countDown();
            var claimed =
                    List.of(one.get(5, TimeUnit.SECONDS), two.get(5, TimeUnit.SECONDS)).stream()
                            .flatMap(Optional::stream)
                            .toList();
            assertThat(claimed).hasSize(1);
            SourceJob old = claimed.getFirst();
            jdbc.sql(
                            "UPDATE source_job SET lease_expires_at=CURRENT_TIMESTAMP-INTERVAL '1"
                                + " second' WHERE id=:id")
                    .param("id", old.view().id())
                    .update();
            assertThat(jobs.renew(old)).isFalse();
            assertThat(jobs.progress(old, 1, 1)).isFalse();
            coordinator.activate(old, List.of(), List.of(), "test", "jobs-v1");
            jobs.recoverExpired();
            assertThat(service.get(realm, submitted.job().id(), user).state())
                    .isEqualTo(SourceJobState.QUEUED);
            assertThat(jobs.claim()).isEmpty();
            jobs.failed(old, "WRONG_OWNER", false);
            assertThat(service.get(realm, submitted.job().id(), user).state())
                    .isEqualTo(SourceJobState.QUEUED);
        }
    }

    @Test
    void leaseExpirationUsesWallClockEvenInsideAnOlderTransaction() {
        submit("clock", "Nara conserva 37 monedas.");
        SourceJob claimed = jobs.claim().orElseThrow();
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.sql("UPDATE source_job SET lease_expires_at=clock_timestamp()+INTERVAL '100 milliseconds' WHERE id=:id")
                .param("id", claimed.view().id()).update();
            jdbc.sql("SELECT pg_sleep(0.15)").query((rs, row) -> true).single();
            assertThat(jobs.progress(claimed, 1, 1)).isFalse();
            assertThat(jobs.renew(claimed)).isFalse();
            assertThat(jobs.lockCurrent(claimed)).isFalse();
        });
    }

    @Test
    void threeTransientAttemptsThenManualRetryKeepsVersionAndHistory() {
        var submitted = submit("fail", "Nara conserva 37 monedas.");
        modelFails.set(true);
        for (int i = 0; i < 3; i++) {
            execute();
            jdbc.sql("UPDATE source_job SET next_attempt_at=CURRENT_TIMESTAMP WHERE id=:id")
                    .param("id", submitted.job().id())
                    .update();
        }
        assertThat(service.get(realm, submitted.job().id(), user).state())
                .isEqualTo(SourceJobState.FAILED);
        service.retry(realm, submitted.job().id(), user);
        service.retry(realm, submitted.job().id(), user);
        modelFails.set(false);
        execute();
        var result = service.get(realm, submitted.job().id(), user);
        assertThat(result.state()).isEqualTo(SourceJobState.SUCCEEDED);
        assertThat(result.versionId()).isEqualTo(submitted.versionId());
        assertThat(result.attempts()).isEqualTo(4);
        assertThat(result.history().stream().filter(e -> e.state() == SourceJobState.RUNNING))
                .hasSize(4);
    }

    @Test
    void failedReplacementPreservesPublishedVersionAndMissingFileCanBeReplaced() throws Exception {
        var first = submit("first", "Nara no entregó las 37 monedas.");
        execute();
        var replacement =
                service.replace(
                        realm,
                        first.documentId(),
                        user,
                        policy,
                        "Nara entregó las monedas.".getBytes(StandardCharsets.UTF_8),
                        "cronica.md",
                        "text/markdown",
                        "replace");
        storage.delete(
                jobs.find(realm, replacement.job().id()).orElseThrow().version().storageKey());
        execute();
        assertThat(service.get(realm, replacement.job().id(), user).errorCode())
                .isEqualTo("FILE_UNAVAILABLE");
        mvc.perform(
                        get("/api/v1/realms/{realm}/sources/{doc}", realm, first.documentId())
                                .with(identity("owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(first.versionId().toString()));
        assertThatThrownBy(() -> service.retry(realm, replacement.job().id(), user))
                .isInstanceOf(SourceJobException.class);
        var recovered =
                service.replace(
                        realm,
                        first.documentId(),
                        user,
                        policy,
                        "Nuevo original válido.".getBytes(StandardCharsets.UTF_8),
                        "cronica.md",
                        "text/markdown",
                        "restore");
        execute();
        assertThat(service.get(realm, recovered.job().id(), user).state())
                .isEqualTo(SourceJobState.SUCCEEDED);
    }

    @Test
    void uploadReconciliationChecksWholeFileAndCancellationPreventsActivation() {
        var present = submit("present", "Archivo íntegro.");
        var missing = submit("missing", "Archivo perdido.");
        storage.delete(jobs.find(realm, missing.job().id()).orElseThrow().version().storageKey());
        jdbc.sql(
                        "UPDATE source_job SET"
                            + " state='UPLOADING',updated_at=CURRENT_TIMESTAMP-INTERVAL '6"
                            + " minutes'")
                .update();
        worker.reconcile();
        assertThat(service.get(realm, present.job().id(), user).state())
                .isEqualTo(SourceJobState.QUEUED);
        assertThat(service.get(realm, missing.job().id(), user).state())
                .isEqualTo(SourceJobState.FAILED);
        SourceJob claimed = jobs.claim().orElseThrow();
        jdbc.sql("UPDATE source_document SET active=false WHERE id=:id")
                .param("id", present.documentId())
                .update();
        jobs.recoverExpired();
        worker.process(claimed);
        assertThat(service.get(realm, present.job().id(), user).state())
                .isEqualTo(SourceJobState.CANCELLED);
        assertThat(
                        jdbc.sql("SELECT count(*) FROM document_version WHERE active")
                                .query(Integer.class)
                                .single())
                .isZero();
    }

    @Test
    void playersAndOtherRealmsCannotInspectProgressOrErrorsAndRevokedEditorCannotPublish()
            throws Exception {
        var submitted = submit("permissions", "Texto reservado.");
        mvc.perform(get("/api/v1/me").with(identity("player"))).andExpect(status().isOk());
        UUID player =
                jdbc.sql("SELECT id FROM codex_user WHERE subject='player'")
                        .query(UUID.class)
                        .single();
        jdbc.sql(
                        "INSERT INTO realm_membership(id,realm_id,user_id,role)"
                            + " VALUES(gen_random_uuid(),:realm,:user,'PLAYER')")
                .param("realm", realm)
                .param("user", player)
                .update();
        for (UUID scope : List.of(realm, UUID.randomUUID())) {
            mvc.perform(get("/api/v1/realms/{realm}/source-jobs", scope).with(identity("player")))
                    .andExpect(status().isNotFound());
            mvc.perform(
                            get(
                                            "/api/v1/realms/{realm}/source-jobs/{id}",
                                            scope,
                                            submitted.job().id())
                                    .with(identity("player")))
                    .andExpect(status().isNotFound());
        }
        SourceJob claimed = jobs.claim().orElseThrow();
        jdbc.sql("UPDATE realm_membership SET active=false WHERE realm_id=:realm AND user_id=:user")
                .param("realm", realm)
                .param("user", user)
                .update();
        worker.process(claimed);
        assertThat(jobs.find(realm, submitted.job().id()).orElseThrow().view().state())
                .isEqualTo(SourceJobState.FAILED);
        assertThat(
                        jdbc.sql("SELECT count(*) FROM document_version WHERE active")
                                .query(Integer.class)
                                .single())
                .isZero();
    }

    static org.springframework.test.web.servlet.request.RequestPostProcessor identity(
            String subject) {
        return jwt().jwt(
                        j ->
                                j.issuer("http://localhost:8180/realms/codex-of-realms")
                                        .subject(subject)
                                        .claim("name", subject));
    }

    @TestConfiguration
    static class Models {
        @Bean
        AtomicBoolean modelFails() {
            return new AtomicBoolean(false);
        }

        @Bean
        @Primary
        TextEmbedding embeddings(AtomicBoolean modelFails) {
            return new TextEmbedding() {
                public List<float[]> embed(List<String> texts) {
                    if (modelFails.get())
                        throw IngestionException.embeddingUnavailable("test temporary failure");
                    return texts.stream().map(t -> new float[] {1, 0, 0}).toList();
                }

                public EmbeddingDescriptor descriptor() {
                    return new EmbeddingDescriptor("test", "jobs-v1");
                }
            };
        }
    }
}
