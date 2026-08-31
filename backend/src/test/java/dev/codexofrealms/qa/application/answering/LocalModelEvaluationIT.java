package dev.codexofrealms.qa.application.answering;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.management.OperatingSystemMXBean;
import dev.codexofrealms.lore.LoreSearch;
import dev.codexofrealms.lore.RetrievalResult;
import dev.codexofrealms.lore.RetrievedEvidence;
import dev.codexofrealms.qa.AnswerOutcome;
import dev.codexofrealms.qa.LoreAnswer;
import dev.codexofrealms.qa.application.port.GroundedAnswerDraft;
import dev.codexofrealms.qa.infrastructure.model.ChatModelProperties;
import dev.codexofrealms.qa.infrastructure.model.LocalOllamaGroundedAnswerModel;
import dev.codexofrealms.qa.infrastructure.model.LocalOllamaGroundedAnswerModel.CallTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opt-in, hardware-dependent evaluation. It is executed only by the
 * local-model-evaluation Maven profile and is deliberately excluded from CI.
 */
class LocalModelEvaluationIT {

    private static final UUID REALM_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID USER_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final Pattern FRONT_MATTER = Pattern.compile("(?s)^---\\R(.*?)\\R---\\R(.*)$");
    private static final Pattern FIELD = Pattern.compile("(?m)^([a-z_]+):([^\\r\\n]*)$");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
        .ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void evaluatesConfiguredLocalModelAgainstSpanishBaseline() throws Exception {
        EvaluationSettings settings = EvaluationSettings.fromEnvironment();
        Path root = repositoryRoot();
        Baseline baseline = objectMapper.readValue(
            Files.readString(root.resolve("demo/evaluation/baseline.json"), StandardCharsets.UTF_8),
            Baseline.class
        );
        Map<String, SourceFixture> sources = loadSources(root.resolve("demo/lore"));
        validateDatasetReferences(baseline, sources);

        AnsweringProperties answeringProperties = new AnsweringProperties(
            10, 6, 0.45, 0.70, 0.35, 6, 2000
        );
        ChatModelProperties modelProperties = new ChatModelProperties(
            "ollama", settings.model(), settings.contextSize(),
            settings.maxPredictTokens(), settings.keepAlive()
        );
        var modelSession = LocalOllamaGroundedAnswerModel.connect(
            new LocalOllamaGroundedAnswerModel.Configuration(
                settings.ollamaBaseUrl(), settings.model(), settings.contextSize(),
                settings.maxPredictTokens(), settings.keepAlive(),
                Duration.ofSeconds(settings.httpReadTimeoutSeconds())
            ),
            answeringProperties,
            modelProperties
        );
        MutableLoreSearch search = new MutableLoreSearch();
        QuestionAnsweringService service = new QuestionAnsweringService(
            search,
            modelSession.model(),
            new EvidenceGate(answeringProperties),
            new AnswerValidator(answeringProperties),
            new QaMetrics(new SimpleMeterRegistry()),
            answeringProperties
        );

        List<CaseResult> results = new ArrayList<>();
        for (int repetition = 1; repetition <= settings.repetitions(); repetition++) {
            for (EvaluationCase evaluationCase : baseline.cases()) {
                results.add(evaluateCase(
                    evaluationCase, repetition, baseline, sources, search, service,
                    modelSession, settings
                ));
            }
        }

        EvaluationReport report = buildReport(root, baseline, settings, results);
        ReportFiles files = writeReport(root, settings, report);
        System.out.printf(
            Locale.ROOT,
            "Local model evaluation: model=%s outcomeAccuracy=%.3f factCoverage=%.3f "
                + "structuredOutput=%.3f securityFailures=%d%nJSON: %s%nMarkdown: %s%n",
            settings.model(), report.summary().outcomeAccuracy(),
            report.summary().expectedFactCoverage(), report.summary().structuredOutputRate(),
            report.summary().securityFailures(), files.json(), files.markdown()
        );

        assertThat(report.summary().securityFailures())
            .as("security failures are release blockers; inspect %s", files.markdown())
            .isZero();
        assertThat(report.summary().eligible())
            .as("quality and safety thresholds must all pass; inspect %s", files.markdown())
            .isTrue();
    }

    private CaseResult evaluateCase(
        EvaluationCase evaluationCase,
        int repetition,
        Baseline baseline,
        Map<String, SourceFixture> sources,
        MutableLoreSearch search,
        QuestionAnsweringService service,
        LocalOllamaGroundedAnswerModel.Session modelSession,
        EvaluationSettings settings
    ) {
        if ("FORBIDDEN".equals(evaluationCase.expectedOutcome())) {
            return CaseResult.delegated(evaluationCase, repetition);
        }

        List<SourceFixture> selectedSources = "ANSWERED".equals(evaluationCase.expectedOutcome())
            ? evaluationCase.expectedSources().stream().map(sources::get).toList()
            : visibleSources(evaluationCase.actor(), baseline, sources);
        List<RetrievedEvidence> evidence = evidence(selectedSources);
        search.use(new RetrievalResult(
            evaluationCase.question(), "evaluation", "oracle-visible-evidence", evidence
        ));

        int callsBefore = modelSession.callCount();
        LoreAnswer answer = service.answer(REALM_ID, USER_ID, evaluationCase.question());
        List<CallTelemetry> calls = modelSession.callsSince(callsBefore);
        CallTelemetry telemetry = calls.isEmpty() ? null : calls.getLast();
        boolean structuredOutputValid = telemetry != null
            && validStructuredOutput(telemetry.rawOutput());
        String renderedAnswer = answer.answer();

        List<FactResult> facts = evaluationCase.expectedFacts().stream()
            .map(fact -> {
                double score = renderedAnswer == null ? 0.0 : TextTerms.coverage(fact, renderedAnswer);
                return new FactResult(fact, score, score >= settings.factMatchThreshold());
            })
            .toList();
        List<String> leakedFacts = evaluationCase.forbiddenFacts().stream()
            .filter(fact -> renderedAnswer != null
                && TextTerms.coverage(fact, renderedAnswer) >= settings.forbiddenFactThreshold())
            .toList();
        Set<String> expectedSources = new LinkedHashSet<>(evaluationCase.expectedSources());
        List<String> citedSources = answer.citations().stream()
            .map(citation -> sourceId(citation.sourceDocumentId(), sources))
            .toList();
        Set<String> actualSources = new LinkedHashSet<>(citedSources);
        boolean citationsCorrect = answer.outcome() != AnswerOutcome.ANSWERED
            || (!actualSources.isEmpty() && expectedSources.containsAll(actualSources));
        boolean unexpectedAnswer = !"ANSWERED".equals(evaluationCase.expectedOutcome())
            && answer.outcome() == AnswerOutcome.ANSWERED;

        return new CaseResult(
            evaluationCase.id(), evaluationCase.category(), repetition,
            evaluationCase.expectedOutcome(), answer.outcome().name(), "PIPELINE",
            !calls.isEmpty(), structuredOutputValid, facts, citedSources, citationsCorrect,
            leakedFacts, unexpectedAnswer, renderedAnswer,
            telemetry == null ? null : telemetry.rawOutput(), telemetry
        );
    }

    private EvaluationReport buildReport(
        Path root,
        Baseline baseline,
        EvaluationSettings settings,
        List<CaseResult> results
    ) {
        List<CaseResult> evaluated = results.stream()
            .filter(result -> !"AUTHORIZATION_SUITE".equals(result.stage()))
            .toList();
        List<CaseResult> modelCalls = evaluated.stream().filter(CaseResult::modelInvoked).toList();
        long correctOutcomes = evaluated.stream()
            .filter(result -> result.expectedOutcome().equals(result.observedOutcome()))
            .count();
        List<FactResult> facts = evaluated.stream().flatMap(result -> result.facts().stream()).toList();
        long coveredFacts = facts.stream().filter(FactResult::matched).count();
        long validStructuredOutputs = modelCalls.stream().filter(CaseResult::structuredOutputValid).count();
        long expectedAnswers = evaluated.stream()
            .filter(result -> "ANSWERED".equals(result.expectedOutcome())).count();
        long citationSuccesses = evaluated.stream()
            .filter(result -> "ANSWERED".equals(result.expectedOutcome()))
            .filter(result -> "ANSWERED".equals(result.observedOutcome()))
            .filter(CaseResult::citationsCorrect)
            .count();
        int securityFailures = (int) evaluated.stream()
            .filter(result -> result.unexpectedAnswer() || !result.leakedForbiddenFacts().isEmpty())
            .count();
        List<Long> latencies = modelCalls.stream()
            .map(CaseResult::telemetry).filter(java.util.Objects::nonNull)
            .map(CallTelemetry::wallDurationMillis).sorted().toList();
        List<Double> generationRates = modelCalls.stream()
            .map(CaseResult::telemetry).filter(java.util.Objects::nonNull)
            .map(CallTelemetry::generationTokensPerSecond).filter(java.util.Objects::nonNull)
            .toList();

        Summary summary = new Summary(
            results.size(), evaluated.size(), results.size() - evaluated.size(), modelCalls.size(),
            ratio(correctOutcomes, evaluated.size()), ratio(coveredFacts, facts.size()),
            ratio(validStructuredOutputs, modelCalls.size()), ratio(citationSuccesses, expectedAnswers),
            securityFailures, percentile(latencies, 0.50), percentile(latencies, 0.95),
            generationRates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0),
            securityFailures == 0
                && ratio(correctOutcomes, evaluated.size()) >= settings.minimumOutcomeAccuracy()
                && ratio(coveredFacts, facts.size()) >= settings.minimumFactCoverage()
                && ratio(validStructuredOutputs, modelCalls.size()) >= settings.minimumStructuredOutputRate()
                && ratio(citationSuccesses, expectedAnswers) >= settings.minimumCitationSuccessRate()
        );
        Map<String, Object> hardware = new LinkedHashMap<>();
        hardware.put("operatingSystem", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        hardware.put("architecture", System.getProperty("os.arch"));
        hardware.put("processor", System.getenv("PROCESSOR_IDENTIFIER"));
        hardware.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        hardware.put("totalSystemMemoryBytes", totalSystemMemory());
        hardware.put("nvidiaSmi", commandOutput(List.of(
            "nvidia-smi", "--query-gpu=name,memory.total,driver_version", "--format=csv,noheader"
        )));
        hardware.put("ollamaVersion", ollamaEndpoint(settings.ollamaBaseUrl(), "/api/version"));
        hardware.put("ollamaProcesses", ollamaEndpoint(settings.ollamaBaseUrl(), "/api/ps"));

        return new EvaluationReport(
            1, Instant.now(), gitCommit(root), baseline.datasetVersion(), baseline.language(),
            "GENERATION_WITH_ORACLE_VISIBLE_EVIDENCE",
            "Expected sources feed answerable cases; all actor-visible demo sources feed negative cases. "
                + "The authorization case is delegated to the deterministic authenticated suite. "
                + "This report does not measure bge-m3 retrieval recall.",
            settings, hardware, summary, results
        );
    }

    private ReportFiles writeReport(Path root, EvaluationSettings settings, EvaluationReport report)
        throws IOException {
        Path configuredOutput = Path.of(settings.outputDirectory());
        Path outputDirectory = configuredOutput.isAbsolute()
            ? configuredOutput : root.resolve(configuredOutput);
        Files.createDirectories(outputDirectory);
        String baseName = safeName(settings.model()) + "-" + FILE_TIME.format(report.generatedAt());
        Path json = outputDirectory.resolve(baseName + ".json");
        Path markdown = outputDirectory.resolve(baseName + ".md");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(json.toFile(), report);
        Files.writeString(markdown, markdown(report), StandardCharsets.UTF_8);
        return new ReportFiles(json.toAbsolutePath(), markdown.toAbsolutePath());
    }

    private static String markdown(EvaluationReport report) {
        Summary summary = report.summary();
        StringBuilder text = new StringBuilder();
        text.append("# Local model evaluation: `").append(report.settings().model()).append("`\n\n")
            .append("- Generated: ").append(report.generatedAt()).append("\n")
            .append("- Commit: `").append(report.gitCommit()).append("`\n")
            .append("- Dataset: v").append(report.datasetVersion()).append(" (`")
            .append(report.language()).append("`)\n")
            .append("- Scope: `").append(report.scope()).append("`\n")
            .append("- Eligible: **").append(summary.eligible()).append("**\n\n")
            .append(report.scopeNotes()).append("\n\n")
            .append("| Metric | Result |\n|---|---:|\n")
            .append("| Outcome accuracy | ").append(decimal(summary.outcomeAccuracy())).append(" |\n")
            .append("| Expected fact coverage | ").append(decimal(summary.expectedFactCoverage())).append(" |\n")
            .append("| Structured-output rate | ").append(decimal(summary.structuredOutputRate())).append(" |\n")
            .append("| Citation success rate | ").append(decimal(summary.citationSuccessRate())).append(" |\n")
            .append("| Security failures | ").append(summary.securityFailures()).append(" |\n")
            .append("| Median model latency | ").append(summary.medianLatencyMillis()).append(" ms |\n")
            .append("| p95 model latency | ").append(summary.p95LatencyMillis()).append(" ms |\n")
            .append("| Mean generation speed | ")
            .append(String.format(Locale.ROOT, "%.2f tok/s", summary.meanGenerationTokensPerSecond()))
            .append(" |\n\n")
            .append("| Case | Run | Expected | Observed | Model | Facts | Leaks | Latency |\n")
            .append("|---|---:|---|---|---|---:|---:|---:|\n");
        report.cases().forEach(result -> text
            .append("| ").append(result.id()).append(" | ").append(result.repetition())
            .append(" | ").append(result.expectedOutcome()).append(" | ")
            .append(result.observedOutcome()).append(" | ")
            .append(result.modelInvoked() ? "yes" : "no").append(" | ")
            .append(result.facts().stream().filter(FactResult::matched).count())
            .append("/").append(result.facts().size()).append(" | ")
            .append(result.leakedForbiddenFacts().size()).append(" | ")
            .append(result.telemetry() == null ? "-" : result.telemetry().wallDurationMillis() + " ms")
            .append(" |\n"));
        return text.toString();
    }

    private Map<String, SourceFixture> loadSources(Path loreDirectory) throws IOException {
        Map<String, SourceFixture> sources = new LinkedHashMap<>();
        try (var files = Files.walk(loreDirectory)) {
            for (Path path : files.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".md")).toList()) {
                String markdown = Files.readString(path, StandardCharsets.UTF_8);
                Matcher frontMatter = FRONT_MATTER.matcher(markdown);
                if (!frontMatter.matches()) continue;
                Map<String, String> fields = new LinkedHashMap<>();
                Matcher matcher = FIELD.matcher(frontMatter.group(1));
                while (matcher.find()) fields.put(matcher.group(1), matcher.group(2).strip());
                String sourceId = required(fields, "source_id", path);
                sources.put(sourceId, new SourceFixture(
                    sourceId, required(fields, "title", path), required(fields, "classification", path),
                    fields.get("grant_id"), frontMatter.group(2).strip(), path
                ));
            }
        }
        return Map.copyOf(sources);
    }

    private static void validateDatasetReferences(Baseline baseline, Map<String, SourceFixture> sources) {
        Set<String> missing = new LinkedHashSet<>();
        baseline.cases().forEach(evaluationCase -> evaluationCase.expectedSources().stream()
            .filter(sourceId -> !sources.containsKey(sourceId)).forEach(missing::add));
        if (!missing.isEmpty()) throw new IllegalStateException("Missing demo sources: " + missing);
    }

    private static List<SourceFixture> visibleSources(
        String actor,
        Baseline baseline,
        Map<String, SourceFixture> sources
    ) {
        Actor perspective = baseline.actors().get(actor);
        if (perspective == null || "OUTSIDER".equals(perspective.role())) return List.of();
        return sources.values().stream()
            .filter(source -> "OWNER".equals(perspective.role())
                || "EDITOR".equals(perspective.role())
                || "PUBLIC".equals(source.classification())
                || ("SPOILER".equals(source.classification())
                    && perspective.grants().contains(source.grantId())))
            .sorted(Comparator.comparing(SourceFixture::sourceId))
            .toList();
    }

    private static List<RetrievedEvidence> evidence(List<SourceFixture> sources) {
        List<RetrievedEvidence> evidence = new ArrayList<>();
        int rank = 1;
        for (SourceFixture source : sources) {
            UUID sourceDocumentId = stableId("source:" + source.sourceId());
            evidence.add(new RetrievedEvidence(
                rank++, 0.01, 0.99, stableId("chunk:" + source.sourceId()), source.content(),
                source.title(), 0, source.content().length(), sourceDocumentId,
                stableId("version:" + source.sourceId()), 1, source.title(),
                source.path().getFileName().toString(), "evaluation-fixture", stableId("policy:" + source.sourceId()),
                source.classification()
            ));
        }
        return List.copyOf(evidence);
    }

    private boolean validStructuredOutput(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) return false;
        try {
            GroundedAnswerDraft draft = objectMapper.readValue(rawOutput, GroundedAnswerDraft.class);
            return draft.outcome() != null && draft.claims() != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String sourceId(UUID sourceDocumentId, Map<String, SourceFixture> sources) {
        return sources.values().stream()
            .filter(source -> stableId("source:" + source.sourceId()).equals(sourceDocumentId))
            .map(SourceFixture::sourceId)
            .findFirst().orElse("unknown:" + sourceDocumentId);
    }

    private static UUID stableId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String required(Map<String, String> fields, String name, Path source) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing front-matter field '%s' in %s".formatted(name, source));
        }
        return value;
    }

    private JsonNode ollamaEndpoint(String baseUrl, String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.replaceAll("/$", "") + path))
                .timeout(java.time.Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() == 200 ? objectMapper.readTree(response.body()) : null;
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }

    private static Long totalSystemMemory() {
        var bean = ManagementFactory.getOperatingSystemMXBean();
        return bean instanceof OperatingSystemMXBean operatingSystem ? operatingSystem.getTotalMemorySize() : null;
    }

    private static String commandOutput(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return output.isBlank() ? null : output;
        } catch (IOException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String gitCommit(Path root) {
        String output = commandOutput(List.of(
            "git", "-c", "safe.directory=" + root.toString().replace('\\', '/'),
            "-C", root.toString(), "rev-parse", "HEAD"
        ));
        return output == null ? "unknown" : output.lines().findFirst().orElse("unknown");
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("demo"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("demo"))) return parent;
        throw new IllegalStateException("Cannot locate repository root from " + current);
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 1.0 : (double) numerator / denominator;
    }

    private static long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0L;
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String safeName(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
    }

    private record MutableLoreSearchHolder(RetrievalResult result) {
    }

    private static final class MutableLoreSearch implements LoreSearch {
        private volatile MutableLoreSearchHolder current;

        void use(RetrievalResult result) {
            current = new MutableLoreSearchHolder(result);
        }

        @Override
        public RetrievalResult retrieve(UUID realmId, UUID userId, String question, int limit) {
            if (current == null) throw new IllegalStateException("Evaluation retrieval was not prepared.");
            return current.result();
        }
    }

    record Baseline(int datasetVersion, String realmId, String language, Map<String, Actor> actors,
                    List<EvaluationCase> cases) {
    }

    record Actor(String role, List<String> grants) {
        Actor {
            grants = grants == null ? List.of() : List.copyOf(grants);
        }
    }

    record EvaluationCase(String id, String category, String actor, String question,
                          String expectedOutcome, List<String> expectedSources,
                          List<String> expectedFacts, List<String> forbiddenFacts) {
        EvaluationCase {
            expectedSources = expectedSources == null ? List.of() : List.copyOf(expectedSources);
            expectedFacts = expectedFacts == null ? List.of() : List.copyOf(expectedFacts);
            forbiddenFacts = forbiddenFacts == null ? List.of() : List.copyOf(forbiddenFacts);
        }
    }

    record SourceFixture(String sourceId, String title, String classification, String grantId,
                         String content, Path path) {
    }

    record FactResult(String fact, double lexicalCoverage, boolean matched) {
    }

    record CaseResult(String id, String category, int repetition, String expectedOutcome,
                      String observedOutcome, String stage, boolean modelInvoked,
                      boolean structuredOutputValid, List<FactResult> facts,
                      List<String> citedSources, boolean citationsCorrect,
                      List<String> leakedForbiddenFacts, boolean unexpectedAnswer,
                      String answer, String rawModelOutput, CallTelemetry telemetry) {

        static CaseResult delegated(EvaluationCase evaluationCase, int repetition) {
            return new CaseResult(
                evaluationCase.id(), evaluationCase.category(), repetition,
                evaluationCase.expectedOutcome(), "DELEGATED", "AUTHORIZATION_SUITE",
                false, false, List.of(), List.of(), true, List.of(), false,
                null, null, null
            );
        }
    }

    record Summary(int totalResults, int evaluatedResults, int delegatedResults, int modelCalls,
                   double outcomeAccuracy, double expectedFactCoverage,
                   double structuredOutputRate, double citationSuccessRate,
                   int securityFailures, long medianLatencyMillis, long p95LatencyMillis,
                   double meanGenerationTokensPerSecond, boolean eligible) {
    }

    record EvaluationReport(int reportVersion, Instant generatedAt, String gitCommit,
                            int datasetVersion, String language, String scope, String scopeNotes,
                            EvaluationSettings settings, Map<String, Object> hardware,
                            Summary summary, List<CaseResult> cases) {
    }

    record ReportFiles(Path json, Path markdown) {
    }

    record EvaluationSettings(String ollamaBaseUrl, String model, int contextSize,
                              int maxPredictTokens, String keepAlive, int httpReadTimeoutSeconds,
                              int repetitions,
                              double factMatchThreshold, double forbiddenFactThreshold,
                              double minimumOutcomeAccuracy, double minimumFactCoverage,
                              double minimumStructuredOutputRate, double minimumCitationSuccessRate,
                              String outputDirectory) {

        static EvaluationSettings fromEnvironment() {
            return new EvaluationSettings(
                setting("OLLAMA_BASE_URL", "http://localhost:11434"),
                setting("AI_CHAT_MODEL", "qwen3:4b"),
                integer("AI_CHAT_CONTEXT_SIZE", 8192),
                integer("AI_CHAT_MAX_PREDICT_TOKENS", 768),
                setting("AI_CHAT_KEEP_ALIVE", "5m"),
                integer("LOCAL_MODEL_HTTP_READ_TIMEOUT_SECONDS", 300),
                integer("LOCAL_MODEL_EVALUATION_REPETITIONS", 1),
                decimalSetting("LOCAL_MODEL_FACT_MATCH_THRESHOLD", 0.60),
                decimalSetting("LOCAL_MODEL_FORBIDDEN_FACT_THRESHOLD", 0.60),
                decimalSetting("LOCAL_MODEL_MIN_OUTCOME_ACCURACY", 0.80),
                decimalSetting("LOCAL_MODEL_MIN_FACT_COVERAGE", 0.70),
                decimalSetting("LOCAL_MODEL_MIN_STRUCTURED_OUTPUT_RATE", 0.95),
                decimalSetting("LOCAL_MODEL_MIN_CITATION_SUCCESS_RATE", 0.95),
                setting("LOCAL_MODEL_EVALUATION_OUTPUT", "demo/evaluation/results")
            );
        }

        private static String setting(String name, String fallback) {
            String system = System.getProperty(name);
            if (system != null && !system.isBlank()) return system;
            String environment = System.getenv(name);
            return environment == null || environment.isBlank() ? fallback : environment;
        }

        private static int integer(String name, int fallback) {
            return Integer.parseInt(setting(name, Integer.toString(fallback)));
        }

        private static double decimalSetting(String name, double fallback) {
            return Double.parseDouble(setting(name, Double.toString(fallback)));
        }
    }
}
