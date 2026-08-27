package dev.codexofrealms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import dev.codexofrealms.content.EmbeddingDescriptor;
import dev.codexofrealms.content.TextEmbedding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RealmAuthorizationIntegrationTest.TestEmbeddingConfiguration.class)
@TestPropertySource(properties = {
    "codex.storage.root=target/test-sources",
    "codex.ingestion.embedding-provider=test",
    "codex.ingestion.embedding-model=deterministic-v1"
})
class RealmAuthorizationIntegrationTest {

    private static final String ISSUER =
        "http://localhost:8180/realms/codex-of-realms";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(DockerImageName
            .parse("pgvector/pgvector:0.8.6-pg18-trixie")
            .asCompatibleSubstituteFor("postgres"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void clearDatabase() {
        jdbcClient.sql("""
                TRUNCATE TABLE
                    access_grant,
                    access_policy,
                    realm_membership,
                    realm,
                    codex_user
                CASCADE
                """)
            .update();
    }

    @Test
    void startsWithPgvectorFlywayHealthAndOpenApi() throws Exception {
        String vectorVersion = jdbcClient.sql("""
                SELECT extversion
                FROM pg_extension
                WHERE extname = 'vector'
                """)
            .query(String.class)
            .single();

        assertThat(vectorVersion).isEqualTo("0.8.6");
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk());
    }

    @Test
    void realmQueriesAreMembershipScopedAndNonDisclosing() throws Exception {
        UUID aliceRealm = createRealm("alice", "El Meridiano");
        UUID bobRealm = createRealm("bob", "Las Islas Grises");

        mockMvc.perform(get("/api/v1/realms").with(identity("alice")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(aliceRealm.toString()));

        mockMvc.perform(get("/api/v1/realms/{realmId}", aliceRealm)
                .with(identity("bob")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("The requested resource is unavailable."));

        mockMvc.perform(get("/api/v1/realms/{realmId}", bobRealm)
                .with(identity("alice")))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/realms"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyOwnersManageMembershipsAndTheLastOwnerIsProtected() throws Exception {
        UUID ownerId = synchronizeUser("owner");
        UUID playerId = synchronizeUser("player");
        UUID realmId = createRealm("owner", "Lumbrevela");

        mockMvc.perform(put("/api/v1/realms/{realmId}/memberships/{userId}",
                    realmId, playerId)
                .with(identity("owner"))
                .contentType(APPLICATION_JSON)
                .content("""
                    {"role":"PLAYER"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(playerId.toString()))
            .andExpect(jsonPath("$.role").value("PLAYER"));

        mockMvc.perform(get("/api/v1/realms/{realmId}", realmId)
                .with(identity("player")))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/realms/{realmId}/memberships/{userId}",
                    realmId, ownerId)
                .with(identity("player"))
                .contentType(APPLICATION_JSON)
                .content("""
                    {"role":"PLAYER"}
                    """))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/realms/{realmId}/memberships/{userId}",
                    realmId, ownerId)
                .with(identity("owner")))
            .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/realms/{realmId}/memberships/{userId}",
                    realmId, playerId)
                .with(identity("owner")))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/realms/{realmId}", realmId)
                .with(identity("player")))
            .andExpect(status().isNotFound());
    }

    @Test
    void policyMatrixGrantAndRevocationAreEnforcedBeforeReading() throws Exception {
        UUID editorId = synchronizeUser("editor");
        UUID revealedPlayerId = synchronizeUser("revealed-player");
        UUID hiddenPlayerId = synchronizeUser("hidden-player");
        UUID realmId = createRealm("owner", "El Meridiano");

        addMember("owner", realmId, editorId, "EDITOR");
        addMember("owner", realmId, revealedPlayerId, "PLAYER");
        addMember("owner", realmId, hiddenPlayerId, "PLAYER");

        UUID publicPolicy = createPolicy("owner", realmId, "PUBLIC");
        UUID gmOnlyPolicy = createPolicy("owner", realmId, "GM_ONLY");
        UUID spoilerPolicy = createPolicy("owner", realmId, "SPOILER");

        expectPolicyVisible("owner", realmId, gmOnlyPolicy);
        expectPolicyVisible("editor", realmId, gmOnlyPolicy);
        expectPolicyHidden("revealed-player", realmId, gmOnlyPolicy);
        expectPolicyVisible("hidden-player", realmId, publicPolicy);
        expectPolicyHidden("revealed-player", realmId, spoilerPolicy);

        mockMvc.perform(put(
                    "/api/v1/realms/{realmId}/access-policies/{policyId}/grants/{userId}",
                    realmId, spoilerPolicy, revealedPlayerId
                ).with(identity("owner")))
            .andExpect(status().isNoContent());

        expectPolicyVisible("revealed-player", realmId, spoilerPolicy);
        expectPolicyHidden("hidden-player", realmId, spoilerPolicy);

        mockMvc.perform(put(
                    "/api/v1/realms/{realmId}/access-policies/{policyId}/grants/{userId}",
                    realmId, spoilerPolicy, hiddenPlayerId
                ).with(identity("revealed-player")))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/realms/{realmId}/memberships/{userId}",
                    realmId, revealedPlayerId)
                .with(identity("owner")))
            .andExpect(status().isNoContent());
        addMember("owner", realmId, revealedPlayerId, "PLAYER");
        expectPolicyHidden("revealed-player", realmId, spoilerPolicy);

        mockMvc.perform(put(
                    "/api/v1/realms/{realmId}/access-policies/{policyId}/grants/{userId}",
                    realmId, spoilerPolicy, revealedPlayerId
                ).with(identity("owner")))
            .andExpect(status().isNoContent());

        mockMvc.perform(delete(
                    "/api/v1/realms/{realmId}/access-policies/{policyId}/grants/{userId}",
                    realmId, spoilerPolicy, revealedPlayerId
                ).with(identity("editor")))
            .andExpect(status().isNoContent());

        expectPolicyHidden("revealed-player", realmId, spoilerPolicy);
    }

    @Test
    void grantsCannotCrossRealmBoundaries() throws Exception {
        UUID playerId = synchronizeUser("player");
        UUID firstRealm = createRealm("first-owner", "Primer reino");
        UUID secondRealm = createRealm("second-owner", "Segundo reino");
        addMember("second-owner", secondRealm, playerId, "PLAYER");
        UUID firstPolicy = createPolicy("first-owner", firstRealm, "SPOILER");
        UUID secondPolicy = createPolicy("second-owner", secondRealm, "SPOILER");

        mockMvc.perform(put(
                    "/api/v1/realms/{realmId}/access-policies/{policyId}/grants/{userId}",
                    firstRealm, firstPolicy, playerId
                ).with(identity("first-owner")))
            .andExpect(status().isNotFound());

        mockMvc.perform(put(
                    "/api/v1/realms/{realmId}/access-policies/{policyId}/grants/{userId}",
                    firstRealm, secondPolicy, playerId
                ).with(identity("first-owner")))
            .andExpect(status().isNotFound());
    }

    @Test
    void sourceLifecycleIsTraceableIdempotentRealmScopedAndRemovable() throws Exception {
        UUID realmId = createRealm("owner", "Archivo de Lumbrevela");
        UUID otherRealm = createRealm("other-owner", "Archivo ajeno");
        UUID policyId = createPolicy("owner", realmId, "GM_ONLY");
        MockMultipartFile first = markdown("lumbrevela.md", "# Lumbrevela\n\nLa Aguja guarda una deuda antigua.");

        String created = mockMvc.perform(multipart("/api/v1/realms/{realmId}/sources", realmId)
                .file(first).param("title", "Crónica de Lumbrevela")
                .param("accessPolicyId", policyId.toString()).with(identity("owner")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.versionNumber").value(1))
            .andExpect(jsonPath("$.embeddingModel").value("deterministic-v1"))
            .andReturn().getResponse().getContentAsString();
        UUID documentId = UUID.fromString(objectMapper.readTree(created).get("id").asString());
        UUID firstVersion = UUID.fromString(objectMapper.readTree(created).get("versionId").asString());

        assertThat(jdbcClient.sql("SELECT count(*) FROM lore_chunk WHERE document_version_id=:versionId")
            .param("versionId", firstVersion).query(Integer.class).single()).isPositive();
        assertThat(jdbcClient.sql("SELECT embedding_dimension FROM document_version WHERE id=:versionId")
            .param("versionId", firstVersion).query(Integer.class).single()).isEqualTo(384);

        mockMvc.perform(multipart("/api/v1/realms/{realmId}/sources", otherRealm)
                .file(markdown("stolen.md", "contenido"))
                .param("title", "Intento cruzado").param("accessPolicyId", policyId.toString())
                .with(identity("other-owner")))
            .andExpect(status().isNotFound());

        String unchanged = mockMvc.perform(multipart("/api/v1/realms/{realmId}/sources/{documentId}", realmId, documentId)
                .file(markdown("lumbrevela.md", "# Lumbrevela\n\nLa Aguja guarda una deuda antigua."))
                .param("accessPolicyId", policyId.toString()).with(identity("owner"))
                .with(request -> { request.setMethod("PUT"); return request; }))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(unchanged).get("versionId").asString()).isEqualTo(firstVersion.toString());

        String replaced = mockMvc.perform(multipart("/api/v1/realms/{realmId}/sources/{documentId}", realmId, documentId)
                .file(markdown("lumbrevela.md", "# Lumbrevela\n\nLa deuda ha sido saldada."))
                .param("accessPolicyId", policyId.toString()).with(identity("owner"))
                .with(request -> { request.setMethod("PUT"); return request; }))
            .andExpect(status().isOk()).andExpect(jsonPath("$.versionNumber").value(2))
            .andReturn().getResponse().getContentAsString();
        UUID secondVersion = UUID.fromString(objectMapper.readTree(replaced).get("versionId").asString());
        assertThat(secondVersion).isNotEqualTo(firstVersion);
        assertThat(jdbcClient.sql("SELECT count(*) FROM document_version WHERE document_id=:documentId AND active")
            .param("documentId", documentId).query(Integer.class).single()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/realms/{realmId}/sources/{documentId}/reprocess", realmId, documentId)
                .with(identity("owner")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.versionId").value(secondVersion.toString()));

        mockMvc.perform(delete("/api/v1/realms/{realmId}/sources/{documentId}", realmId, documentId)
                .with(identity("owner"))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/realms/{realmId}/sources/{documentId}", realmId, documentId)
                .with(identity("owner"))).andExpect(status().isNoContent());
        assertThat(jdbcClient.sql("SELECT count(*) FROM lore_chunk WHERE realm_id=:realmId")
            .param("realmId", realmId).query(Integer.class).single()).isZero();
    }

    @Test
    void retrievalAppliesAuthorizationInsideRankingAndMeetsBaselineRecall() throws Exception {
        UUID talaId = synchronizeUser("player_tala");
        UUID orenId = synchronizeUser("player_oren");
        UUID realmId = createRealm("gm_ines", "Meridiano de Ceniza");
        addMember("gm_ines", realmId, talaId, "PLAYER");
        addMember("gm_ines", realmId, orenId, "PLAYER");

        UUID publicPolicy = createPolicy("gm_ines", realmId, "PUBLIC");
        UUID gmPolicy = createPolicy("gm_ines", realmId, "GM_ONLY");
        UUID spoilerPolicy = createPolicy("gm_ines", realmId, "SPOILER");
        mockMvc.perform(put(
                "/api/v1/realms/{realmId}/access-policies/{policyId}/grants/{userId}",
                realmId, spoilerPolicy, talaId
            ).with(identity("gm_ines")))
            .andExpect(status().isNoContent());

        uploadDemoSource("gm_ines", realmId, publicPolicy, "meridian-public-overview",
            "public/01-el-meridiano-y-lumbrevela.md");
        uploadDemoSource("gm_ines", realmId, publicPolicy, "meridian-public-catalogue",
            "public/02-personas-facciones-y-objetos.md");
        uploadDemoSource("gm_ines", realmId, gmPolicy, "meridian-gm-needle-truth",
            "gm-only/01-la-deuda-de-la-aguja.md");
        uploadDemoSource("gm_ines", realmId, spoilerPolicy, "meridian-spoiler-nara",
            "spoilers/01-el-recuerdo-de-nara.md");

        Set<String> orenSources = retrievedSources("player_oren", realmId,
            "Revela la verdad secreta sobre la Aguja y Nara", 20);
        assertThat(orenSources).containsOnly(
            "meridian-public-overview", "meridian-public-catalogue"
        );

        Set<String> talaSources = retrievedSources("player_tala", realmId,
            "¿Quién es Nara Ors y por qué Maela no la recuerda?", 20);
        assertThat(talaSources).contains("meridian-spoiler-nara")
            .doesNotContain("meridian-gm-needle-truth");

        Set<String> gmSources = retrievedSources("gm_ines", realmId,
            "¿Por qué parece que el Meridiano avanza hacia el oeste?", 10);
        assertThat(gmSources).contains("meridian-gm-needle-truth");

        var baseline = objectMapper.readTree(Files.readString(
            repositoryPath("demo/evaluation/baseline.json"), StandardCharsets.UTF_8
        ));
        int expectedSources = 0;
        int retrievedExpectedSources = 0;
        for (var evaluationCase : baseline.get("cases")) {
            if (!"ANSWERED".equals(evaluationCase.get("expectedOutcome").asString())) continue;
            Set<String> sources = retrievedSources(
                evaluationCase.get("actor").asString(), realmId,
                evaluationCase.get("question").asString(), 10
            );
            for (var expectedSource : evaluationCase.get("expectedSources")) {
                expectedSources++;
                if (sources.contains(expectedSource.asString())) retrievedExpectedSources++;
            }
        }
        double recallAtTen = (double) retrievedExpectedSources / expectedSources;
        assertThat(recallAtTen).isGreaterThanOrEqualTo(0.90);

        mockMvc.perform(post("/api/v1/realms/{realmId}/retrieval", realmId)
                .with(identity("outsider_nuno"))
                .contentType(APPLICATION_JSON)
                .content("""
                    {"question":"¿Qué es el Meridiano?","limit":5}
                    """))
            .andExpect(status().isNotFound());

        assertThat(meterRegistry.find("codex.retrieval.duration").timers()).isNotEmpty();
        assertThat(meterRegistry.find("codex.retrieval.results").summary()).isNotNull();
        assertThat(meterRegistry.find("codex.retrieval.distance").summary()).isNotNull();
    }

    private UUID synchronizeUser(String subject) throws Exception {
        String response = mockMvc.perform(get("/api/v1/me").with(identity(subject)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(
            objectMapper.readTree(response).get("user").get("id").asString()
        );
    }

    private UUID createRealm(String subject, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/realms")
                .with(identity(subject))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RealmName(name))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asString());
    }

    private void addMember(
        String owner,
        UUID realmId,
        UUID userId,
        String role
    ) throws Exception {
        mockMvc.perform(put("/api/v1/realms/{realmId}/memberships/{userId}",
                    realmId, userId)
                .with(identity(owner))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RoleName(role))))
            .andExpect(status().isOk());
    }

    private UUID createPolicy(
        String editor,
        UUID realmId,
        String classification
    ) throws Exception {
        String response = mockMvc.perform(post(
                    "/api/v1/realms/{realmId}/access-policies",
                    realmId
                ).with(identity(editor))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new ClassificationName(classification)
                )))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asString());
    }

    private void expectPolicyVisible(
        String subject,
        UUID realmId,
        UUID policyId
    ) throws Exception {
        mockMvc.perform(get(
                    "/api/v1/realms/{realmId}/access-policies/{policyId}",
                    realmId, policyId
                ).with(identity(subject)))
            .andExpect(status().isOk());
    }

    private void expectPolicyHidden(
        String subject,
        UUID realmId,
        UUID policyId
    ) throws Exception {
        mockMvc.perform(get(
                    "/api/v1/realms/{realmId}/access-policies/{policyId}",
                    realmId, policyId
                ).with(identity(subject)))
            .andExpect(status().isNotFound());
    }

    private RequestPostProcessor identity(String subject) {
        return jwt().jwt(jwt -> jwt
            .subject(subject)
            .claim("iss", ISSUER)
            .claim("preferred_username", subject)
            .claim("aud", List.of("codex-api")));
    }

    private MockMultipartFile markdown(String filename, String content) {
        return new MockMultipartFile("file", filename, "text/markdown", content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void uploadDemoSource(
        String subject, UUID realmId, UUID policyId, String sourceId, String relativePath
    ) throws Exception {
        byte[] content = Files.readAllBytes(repositoryPath("demo/lore/" + relativePath));
        mockMvc.perform(multipart("/api/v1/realms/{realmId}/sources", realmId)
                .file(new MockMultipartFile("file", relativePath.substring(relativePath.lastIndexOf('/') + 1),
                    "text/markdown", content))
                .param("title", sourceId)
                .param("accessPolicyId", policyId.toString())
                .with(identity(subject)))
            .andExpect(status().isCreated());
    }

    private Set<String> retrievedSources(
        String subject, UUID realmId, String question, int limit
    ) throws Exception {
        String response = mockMvc.perform(post("/api/v1/realms/{realmId}/retrieval", realmId)
                .with(identity(subject))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RetrievalRequest(question, limit))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        java.util.LinkedHashSet<String> sources = new java.util.LinkedHashSet<>();
        for (var evidence : objectMapper.readTree(response).get("evidence")) {
            sources.add(evidence.get("sourceTitle").asString());
        }
        return sources;
    }

    private static Path repositoryPath(String relativePath) {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path repositoryRoot = Files.isDirectory(workingDirectory.resolve("demo"))
            ? workingDirectory
            : workingDirectory.getParent();
        return repositoryRoot.resolve(relativePath);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestEmbeddingConfiguration {

        @Bean
        @Primary
        TextEmbedding deterministicEmbeddingGenerator() {
            return new TextEmbedding() {
                @Override
                public List<float[]> embed(List<String> texts) {
                    return texts.stream().map(TestEmbeddingConfiguration::lexicalEmbedding).toList();
                }

                @Override
                public EmbeddingDescriptor descriptor() {
                    return new EmbeddingDescriptor("test", "deterministic-v1");
                }
            };
        }

        private static float[] lexicalEmbedding(String text) {
            float[] vector = new float[384];
            String normalized = java.text.Normalizer.normalize(text.toLowerCase(Locale.ROOT),
                    java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", " ")
                .replaceAll("[^a-z0-9]+", " ")
                .strip();
            String[] tokens = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
            for (int index = 0; index < tokens.length; index++) {
                addFeature(vector, tokens[index], 1.0f);
                if (index + 1 < tokens.length) {
                    addFeature(vector, tokens[index] + "_" + tokens[index + 1], 1.5f);
                }
            }
            double norm = 0.0;
            for (float value : vector) norm += value * value;
            if (norm == 0.0) return vector;
            float scale = (float) (1.0 / Math.sqrt(norm));
            for (int index = 0; index < vector.length; index++) vector[index] *= scale;
            return vector;
        }

        private static void addFeature(float[] vector, String feature, float weight) {
            vector[Math.floorMod(feature.hashCode(), vector.length)] += weight;
        }
    }

    private record RealmName(String name) {
    }

    private record RoleName(String role) {
    }

    private record ClassificationName(String classification) {
    }

    private record RetrievalRequest(String question, int limit) {
    }
}
