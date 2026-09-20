package dev.codexofrealms.qa.application.answering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.codexofrealms.lore.*;
import dev.codexofrealms.qa.*;
import dev.codexofrealms.qa.application.port.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Opt-in: ephemeral database, real ingestion/embedding/retrieval/model, injected authenticated test identities. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(ReferenceEvaluationConfiguration.class)
@TestPropertySource(properties = {
    "codex.storage.root=target/e2e-model-sources",
    "spring.ai.model.chat=ollama", "spring.ai.model.embedding=ollama",
    "codex.ingestion.embedding-provider=ollama", "codex.ingestion.embedding-model=bge-m3",
    "codex.qa.chat-provider=ollama", "spring.ai.ollama.embedding.model=bge-m3"
})
class EndToEndModelEvaluationIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
        DockerImageName.parse("pgvector/pgvector:0.8.6-pg18-trixie").asCompatibleSubstituteFor("postgres"));
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcClient jdbc;
    @Autowired AnsweringProperties properties;
    @MockitoSpyBean dev.codexofrealms.content.TextEmbedding embedding;
    @MockitoSpyBean dev.codexofrealms.lore.application.port.LoreRetriever retriever;
    @MockitoSpyBean EvidenceGate gate;
    @MockitoSpyBean LoreSearch search;
    @MockitoSpyBean LoreEvidence evidence;
    @MockitoSpyBean GroundedAnswerModel model;
    @MockitoSpyBean org.springframework.ai.chat.model.ChatModel chat;
    private final Map<UUID, LocalModelEvaluationIT.SourceFixture> documents = new LinkedHashMap<>();
    private final Map<String, UUID> users = new LinkedHashMap<>();
    private UUID realm;
    private JsonNode actors;
    private final Map<String, Object> trace = new LinkedHashMap<>();

    @Test
    void evaluatesRealPipeline() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        var dataset = json.readTree(Files.readString(root.resolve(System.getenv().getOrDefault("IA_DATASET", "demo/evaluation/ia-v3.json"))));
        var sources = new LinkedHashMap<>(new LocalModelEvaluationIT().loadSources(root.resolve("demo/lore")));
        sources.putAll(new LocalModelEvaluationIT().loadSources(root.resolve("demo/evaluation/ia-sources")));
        if (dataset.get("datasetVersion").asInt() >= 4 || Integer.parseInt(System.getenv().getOrDefault("IA_CORPUS_VERSION", "3")) >= 4)
            sources.putAll(new LocalModelEvaluationIT().loadSources(root.resolve("demo/evaluation/ia-v4-sources")));
        if (dataset.get("datasetVersion").asInt() >= 5 || Integer.parseInt(System.getenv().getOrDefault("IA_CORPUS_VERSION", "3")) >= 5)
            sources.putAll(new LocalModelEvaluationIT().loadSources(root.resolve("demo/evaluation/ia-v5-sources")));
        seed(dataset, sources);
        if ("true".equals(System.getenv("IA_EMBEDDING_AUDIT"))) auditEmbeddings();
        instrument();
        String split = System.getenv().getOrDefault("IA_SPLIT", "validation");
        int repetitions = Integer.parseInt(System.getenv().getOrDefault("LOCAL_MODEL_EVALUATION_REPETITIONS", "3"));
        var cases = new ArrayList<JsonNode>();
        for (var c : dataset.get("cases")) if (split.equals(c.path("split").asString())) cases.add(c);
        assertThat(cases).isNotEmpty();
        // Report cold start separately. Warm-up executes the same public API as measured requests.
        var warmup = dataset.get("cases").get(0);
        long start = System.nanoTime();
        var warmupResponse = ask(warmup);
        assertThat(warmupResponse.getStatus()).as("warm-up HTTP status").isEqualTo(200);
        assertThat(trace.get("draft")).as("warm-up must complete a real model call before timed repetitions").isNotNull();
        long coldMillis = millis(start);
        var rows = new ArrayList<Map<String, Object>>();
        for (int repetition = 1; repetition <= repetitions; repetition++) {
            for (var c : cases) {
                var row = evaluate(c, repetition);
                rows.add(row);
                System.out.printf("IA case %s run %d: %s (%s ms)%n", row.get("id"), repetition,
                    row.get("observedOutcome"), row.get("totalMillis"));
            }
        }
        var summaries = new ArrayList<Map<String, Object>>();
        for (int r = 1; r <= repetitions; r++) {
            int repetition = r;
            summaries.add(summarize(rows.stream().filter(row -> row.get("repetition").equals(repetition)).toList()));
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("generatedAt", Instant.now().toString()); report.put("datasetVersion", dataset.get("datasetVersion").asInt());
        report.put("scope", "real ingestion + bge-m3 + pgvector + production selector; test JWT identities");
        report.put("pipeline", System.getenv().getOrDefault("IA_PIPELINE", "current"));
        report.put("retrievalVersion", System.getenv().getOrDefault("LORE_HYBRID_ENABLED", "false").equals("true") ? "hybrid-v1" : "vector-v1");
        report.put("contextualPassages", System.getenv().getOrDefault("LORE_CONTEXTUAL_PASSAGES_ENABLED", "false"));
        report.put("completeSelection", System.getenv().getOrDefault("QA_COMPLETE_SELECTION_ENABLED", "false"));
        report.put("promptVersion", System.getenv().getOrDefault("QA_SELECTION_PROMPT_VERSION", "v1"));
        report.put("lexicalMinimumCoverage", System.getenv().getOrDefault("LORE_LEXICAL_MINIMUM_COVERAGE", "1.0"));
        report.put("split", split); report.put("properties", properties);
        report.put("model", model.descriptor()); report.put("coldStartMillis", coldMillis);
        report.put("repetitions", summaries); report.put("cases", rows);
        Path output = Path.of(Objects.requireNonNull(System.getenv("IA_REPORT"), "IA_REPORT required"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        // Quality failures are report data, not technical failures. The runner decides eligibility.
        assertThat(rows).noneMatch(row -> row.containsKey("technicalError"));
    }

    private void instrument() {
        doAnswer(call -> {
            long start = System.nanoTime();
            try { return call.callRealMethod(); } finally { trace.put("embeddingMillis", millis(start)); }
        }).when(embedding).embed(anyString());
        doAnswer(call -> {
            long start = System.nanoTime();
            try { return call.callRealMethod(); } finally { trace.put("databaseRetrievalMillis", millis(start)); }
        }).when(retriever).retrieve(any());
        doAnswer(call -> {
            var result = (EvidenceGateDecision) call.callRealMethod();
            trace.put("initialGateReason", result.reason().name());
            return result;
        }).when(gate).evaluate(anyString(), anyList());
        doAnswer(call -> {
            var result = (EvidenceGateDecision) call.callRealMethod();
            trace.put("passageGateReason", result.reason().name());
            return result;
        }).when(gate).screenPassages(anyString(), anyList());
        doAnswer(call -> {
            var response = (org.springframework.ai.chat.model.ChatResponse) call.callRealMethod();
            trace.put("usage", response.getMetadata().getUsage());
            trace.put("rawModelOutput", response.getResult().getOutput().getText());
            return response;
        }).when(chat).call(any(org.springframework.ai.chat.prompt.Prompt.class));
        doAnswer(call -> {
            long start = System.nanoTime();
            try { var result = (RetrievalResult) call.callRealMethod(); trace.put("retrieved", result.evidence());
                trace.put("retrievedCoverage", PassageRelevance.diagnostics(call.getArgument(2), result.evidence().stream()
                    .map(RetrievedEvidence::content).collect(java.util.stream.Collectors.joining("\n"))));
                trace.put("maximumSimilarity", result.evidence().stream().mapToDouble(RetrievedEvidence::similarity).max().orElse(0));
                trace.put("retrievalSignals", result.evidence().stream().map(p -> Map.of("chunkId", p.chunkId(), "signals", p.retrievalSignals())).toList());
                return result; }
            finally { trace.put("retrievalMillis", millis(start)); }
        }).when(search).retrieve(any(), any(), anyString(), anyInt());
        doAnswer(call -> {
            long start = System.nanoTime();
            try { var result = call.callRealMethod(); trace.put("offered", result);
                @SuppressWarnings("unchecked") var passages = (List<RetrievedEvidence>) result;
                trace.put("passageCoverage", PassageRelevance.diagnostics(call.getArgument(2), passages.stream()
                    .map(RetrievedEvidence::content).collect(java.util.stream.Collectors.joining("\n"))));
                return result; }
            finally { trace.put("passagesMillis", millis(start)); }
        }).when(evidence).passages(any(), any(), anyString(), anyList(), anyInt());
        doAnswer(call -> {
            long start = System.nanoTime(); trace.put("modelInvoked", true);
            try { var result = (GroundedAnswerDraft) call.callRealMethod(); trace.put("draft", result); return result; }
            finally { trace.put("modelMillis", millis(start)); }
        }).when(model).generate(any());
    }

    private Map<String, Object> evaluate(JsonNode c, int repetition) throws Exception {
        trace.clear();
        var row = new LinkedHashMap<String, Object>();
        row.put("id", c.get("id").asString()); row.put("repetition", repetition);
        row.put("expectedOutcome", c.get("expectedOutcome").asString());
        long start = System.nanoTime();
        var response = ask(c);
        row.put("totalMillis", millis(start)); row.putAll(trace);
        boolean forbidden = "FORBIDDEN".equals(c.get("expectedOutcome").asString());
        String observed = response.getStatus() == 404 || response.getStatus() == 403 ? "FORBIDDEN" : "ERROR";
        LoreAnswer answer = null;
        if (response.getStatus() == 200) {
            answer = json.readValue(response.getContentAsString(), LoreAnswer.class);
            observed = answer.outcome().name(); row.put("answer", answer);
        } else if (!forbidden || !observed.equals("FORBIDDEN")) row.put("technicalError", response.getContentAsString());
        row.put("observedOutcome", observed);
        if (trace.get("draft") instanceof GroundedAnswerDraft draft && trace.get("offered") instanceof List<?> items) {
            var offered = items.stream().map(RetrievedEvidence.class::cast).toList();
            row.put("missingQuestionParts", SelectionCompleteness.missing(c.get("question").asString(), offered,
                offered.stream().filter(p -> draft.passageIds().contains(p.passageId())).toList()));
        }
        String text = answer == null || answer.answer() == null ? "" : answer.answer();
        var cited = new LinkedHashSet<String>();
        boolean literal = true, accessSafe = true;
        for (String stage : List.of("retrieved", "offered")) {
            if (trace.get(stage) instanceof List<?> passages) for (Object item : passages) {
                var passage = (RetrievedEvidence) item;
                accessSafe &= visible(c.get("actor").asString(), documents.get(passage.sourceDocumentId()));
            }
        }
        if (answer != null) for (var excerpt : answer.excerpts()) {
            var citation = answer.citations().stream().filter(it -> it.rank() == excerpt.citationRank()).findFirst().orElseThrow();
            var source = documents.get(citation.sourceDocumentId());
            literal &= source != null && citation.startOffset() >= 0 && citation.endOffset() <= source.content().length()
                && citation.startOffset() < citation.endOffset()
                && source.content().substring(citation.startOffset(), citation.endOffset()).equals(excerpt.text());
            if (source != null) cited.add(source.sourceId());
            var check = mvc.perform(get("/api/v1/realms/{r}/sources/{d}/versions/{v}/content", realm,
                citation.sourceDocumentId(), citation.documentVersionId()).with(identity(c.get("actor").asString())))
                .andReturn().getResponse();
            accessSafe &= check.getStatus() == 200;
        }
        int matched = 0, facts = 0;
        for (var fact : c.get("expectedFacts")) { facts++; if (TextTerms.coverage(fact.asString(), text) >= .60) matched++; }
        boolean leaked = false;
        for (var fact : c.get("forbiddenFacts")) leaked |= TextTerms.coverage(fact.asString(), text) >= .60;
        boolean citations = literal && accessSafe && !cited.isEmpty();
        for (var source : c.get("expectedSources")) citations &= cited.contains(source.asString());
        boolean context = true;
        for (var quote : c.path("requiredQuotes")) context &= text.contains(quote.asString());
        row.put("citedSources", cited); row.put("literal", literal); row.put("facts", facts); row.put("matchedFacts", matched);
        row.put("citationSuccess", citations); row.put("contextPassed", context);
        row.put("mandatory", c.path("mandatory").asBoolean(false));
        row.put("securityFailure", !literal || !accessSafe || leaked || (forbidden && !observed.equals("FORBIDDEN")));
        row.put("unexpectedAnswer", !c.get("expectedOutcome").asString().equals("ANSWERED") && observed.equals("ANSWERED"));
        row.put("structured", !trace.containsKey("modelInvoked") || trace.get("draft") instanceof GroundedAnswerDraft draft && draft.outcome() != null);
        return row;
    }

    static Map<String, Object> summarize(List<Map<String, Object>> rows) {
        int answered = 0, citations = 0, facts = 0, matched = 0, correct = 0, structured = 0, calls = 0, security = 0, unexpected = 0, refused = 0;
        boolean mandatory = true;
        for (var r : rows) {
            boolean expected = "ANSWERED".equals(r.get("expectedOutcome"));
            boolean ok = Objects.equals(r.get("expectedOutcome"), r.get("observedOutcome"));
            if (ok) correct++;
            if (expected) { answered++; if (Boolean.TRUE.equals(r.get("citationSuccess"))) citations++; if (!ok) refused++; }
            if (Boolean.TRUE.equals(r.get("modelInvoked"))) { calls++; if (Boolean.TRUE.equals(r.get("structured"))) structured++; }
            facts += (int) r.get("facts"); matched += (int) r.get("matchedFacts");
            if (Boolean.TRUE.equals(r.get("securityFailure"))) security++;
            if (Boolean.TRUE.equals(r.get("unexpectedAnswer"))) unexpected++;
            if (Boolean.TRUE.equals(r.get("mandatory"))) mandatory &= ok && Boolean.TRUE.equals(r.get("contextPassed"))
                && (!expected || Boolean.TRUE.equals(r.get("citationSuccess")));
        }
        var times = rows.stream().mapToLong(r -> (long) r.get("totalMillis")).sorted().toArray();
        double accuracy = (double) correct / rows.size(), coverage = facts == 0 ? 1 : (double) matched / facts;
        double structuredRate = calls == 0 ? 0 : (double) structured / calls, citationRate = answered == 0 ? 0 : (double) citations / answered;
        var summary = new LinkedHashMap<String, Object>();
        summary.put("outcomeAccuracy", accuracy); summary.put("factCoverage", coverage);
        summary.put("structuredOutput", structuredRate); summary.put("citationSuccess", citationRate);
        summary.put("securityFailures", security); summary.put("unexpectedAnswers", unexpected); summary.put("answerableRefusals", refused);
        summary.put("mandatoryPassed", mandatory);
        summary.put("warmP95Millis", times[(int) Math.ceil(times.length * .95) - 1]);
        summary.put("eligible", accuracy >= .8 && coverage >= .7 && structuredRate >= .95 && citationRate >= .95
            && security == 0 && unexpected == 0 && mandatory);
        return summary;
    }

    private org.springframework.mock.web.MockHttpServletResponse ask(JsonNode c) throws Exception {
        return mvc.perform(post("/api/v1/realms/{r}/questions", realm).with(identity(c.get("actor").asString()))
            .contentType("application/json").content(json.writeValueAsString(Map.of("question", c.get("question").asString()))))
            .andReturn().getResponse();
    }
    private void seed(JsonNode dataset, Map<String, LocalModelEvaluationIT.SourceFixture> sources) throws Exception {
        actors = dataset.get("actors");
        for (var entry : dataset.get("actors").properties()) {
            var response = mvc.perform(get("/api/v1/me").with(identity(entry.getKey()))).andExpect(status().isOk()).andReturn().getResponse();
            users.put(entry.getKey(), UUID.fromString(json.readTree(response.getContentAsString()).get("user").get("id").asString()));
        }
        realm = UUID.fromString(send(post("/api/v1/realms"), Map.of("name", "IA evaluation"), 201).get("id").asString());
        for (var entry : dataset.get("actors").properties()) {
            String role = entry.getValue().get("role").asString();
            if (!role.equals("OWNER") && !role.equals("OUTSIDER")) send(put("/api/v1/realms/{r}/memberships/{u}", realm, users.get(entry.getKey())), Map.of("role", role), 200);
        }
        for (var source : sources.values().stream().sorted(Comparator.comparing(LocalModelEvaluationIT.SourceFixture::sourceId)).toList()) {
            UUID policy = UUID.fromString(send(post("/api/v1/realms/{r}/access-policies", realm), Map.of("classification", source.classification()), 201).get("id").asString());
            for (var entry : dataset.get("actors").properties()) for (var grant : entry.getValue().get("grants")) {
                if (grant.asString().equals(source.grantId())) mvc.perform(put("/api/v1/realms/{r}/access-policies/{p}/grants/{u}", realm, policy, users.get(entry.getKey()))
                    .with(identity("gm_ines"))).andExpect(status().isNoContent());
            }
            var response = mvc.perform(multipart("/api/v1/realms/{r}/sources", realm)
                .file(new MockMultipartFile("file", source.sourceId() + ".md", "text/markdown", source.content().getBytes(StandardCharsets.UTF_8)))
                .param("title", source.title()).param("accessPolicyId", policy.toString()).header("Idempotency-Key", UUID.randomUUID().toString())
                .with(identity("gm_ines"))).andExpect(status().isAccepted()).andReturn().getResponse();
            var submission = json.readTree(response.getContentAsString());
            documents.put(UUID.fromString(submission.get("documentId").asString()), source);
            UUID job = UUID.fromString(submission.get("job").get("id").asString());
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
            String state = "";
            while (System.nanoTime() < deadline) {
                state = jdbc.sql("SELECT state FROM source_job WHERE id=:id").param("id", job).query(String.class).single();
                if (List.of("SUCCEEDED", "FAILED", "CANCELLED").contains(state)) break;
                Thread.sleep(100);
            }
            assertThat(state).as("ingestion %s", source.sourceId()).isEqualTo("SUCCEEDED");
        }
    }
    private JsonNode send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Object body, int expected) throws Exception {
        return json.readTree(mvc.perform(request.with(identity("gm_ines")).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
    private RequestPostProcessor identity(String subject) {
        return jwt().jwt(token -> token.subject(subject).claim("iss", "http://localhost:8180/realms/codex-of-realms")
            .claim("preferred_username", subject).claim("aud", List.of("codex-api")));
    }
    private boolean visible(String actor, LocalModelEvaluationIT.SourceFixture source) {
        if (source == null) return false;
        String role = actors.get(actor).get("role").asString();
        if (role.equals("OUTSIDER")) return false;
        if (role.equals("OWNER") || role.equals("EDITOR") || source.classification().equals("PUBLIC")) return true;
        for (var grant : actors.get(actor).get("grants"))
            if (source.classification().equals("SPOILER") && grant.asString().equals(source.grantId())) return true;
        return false;
    }
    private void auditEmbeddings() throws Exception {
        var chunks = jdbc.sql("SELECT id, content, embedding::text AS vector FROM lore_chunk ORDER BY document_version_id, ordinal")
            .query((rs, row) -> Map.of("id", rs.getString("id"), "text", rs.getString("content"), "vector", rs.getString("vector"))).list();
        var results = new ArrayList<Map<String, Object>>();
        for (var chunk : chunks) {
            float[] actual = embedding.embed(chunk.get("text"));
            float[] stored = json.readValue(chunk.get("vector"), float[].class);
            double dot = 0, a = 0, b = 0;
            assertThat(actual.length).isEqualTo(stored.length);
            for (int i = 0; i < actual.length; i++) { dot += actual[i] * stored[i]; a += actual[i]*actual[i]; b += stored[i]*stored[i]; }
            results.add(Map.of("chunkId", chunk.get("id"), "cosine", dot / Math.sqrt(a*b)));
        }
        Path report = Path.of(System.getenv("IA_REPORT") + ".embeddings.json");
        Files.writeString(report, json.writerWithDefaultPrettyPrinter().writeValueAsString(results));
        assertThat(results).allSatisfy(r -> assertThat((double) r.get("cosine")).as("stored versus individual %s", r.get("chunkId")).isGreaterThan(.999));
    }
    private static long millis(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start); }
}
