package dev.codexofrealms.qa.infrastructure;

import dev.codexofrealms.qa.GroundedAnswerModel;
import dev.codexofrealms.qa.application.QaProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test-only factory for the explicitly invoked local-model evaluation profile.
 */
public final class LocalOllamaGroundedAnswerModel {

    private LocalOllamaGroundedAnswerModel() {
    }

    public static Session connect(Configuration configuration, QaProperties properties) {
        OllamaApi api = OllamaApi.builder().baseUrl(configuration.baseUrl()).build();
        var available = api.listModels().models().stream()
            .map(model -> model.name() == null ? model.model() : model.name())
            .toList();
        if (available.stream().noneMatch(configuration.model()::equals)) {
            throw new IllegalStateException(
                "Ollama model '%s' is not installed. Available models: %s. Pull it explicitly first."
                    .formatted(configuration.model(), available)
            );
        }

        OllamaChatOptions options = OllamaChatOptions.builder()
            .model(configuration.model())
            .temperature(0.0)
            .numCtx(configuration.contextSize())
            .numPredict(configuration.maxPredictTokens())
            .keepAlive(configuration.keepAlive())
            .disableThinking()
            .build();
        ChatModel delegate = OllamaChatModel.builder()
            .ollamaApi(api)
            .options(options)
            .build();
        TelemetryChatModel instrumented = new TelemetryChatModel(delegate);

        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("chatModel", instrumented);
        GroundedAnswerModel model = new SpringAiGroundedAnswerModel(
            beans.getBeanProvider(ChatModel.class), JsonMapper.builder().build(), properties
        );
        return new Session(model, instrumented);
    }

    public record Configuration(
        String baseUrl,
        String model,
        int contextSize,
        int maxPredictTokens,
        String keepAlive
    ) {
    }

    public record CallTelemetry(
        long wallDurationMillis,
        Long totalDurationMillis,
        Long loadDurationMillis,
        Long promptEvalDurationMillis,
        Long evalDurationMillis,
        Integer promptTokens,
        Integer completionTokens,
        Double generationTokensPerSecond,
        String rawOutput,
        String failure
    ) {
    }

    public static final class Session {

        private final GroundedAnswerModel model;
        private final TelemetryChatModel telemetry;

        private Session(GroundedAnswerModel model, TelemetryChatModel telemetry) {
            this.model = model;
            this.telemetry = telemetry;
        }

        public GroundedAnswerModel model() {
            return model;
        }

        public int callCount() {
            return telemetry.calls().size();
        }

        public List<CallTelemetry> callsSince(int previousCount) {
            List<CallTelemetry> calls = telemetry.calls();
            return List.copyOf(calls.subList(previousCount, calls.size()));
        }
    }

    private static final class TelemetryChatModel implements ChatModel {

        private final ChatModel delegate;
        private final List<CallTelemetry> calls = new ArrayList<>();

        private TelemetryChatModel(ChatModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            long started = System.nanoTime();
            try {
                ChatResponse response = delegate.call(prompt);
                calls.add(telemetry(started, response, null));
                return response;
            } catch (RuntimeException exception) {
                calls.add(telemetry(started, null,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()));
                throw exception;
            }
        }

        private List<CallTelemetry> calls() {
            return calls;
        }

        private static CallTelemetry telemetry(
            long started,
            ChatResponse response,
            String failure
        ) {
            long wallMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            if (response == null) {
                return new CallTelemetry(
                    wallMillis, null, null, null, null, null, null, null, null, failure
                );
            }
            ChatResponseMetadata metadata = response.getMetadata();
            Usage usage = metadata == null ? null : metadata.getUsage();
            Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
            Duration evalDuration = duration(metadata, "eval-duration");
            Double tokensPerSecond = completionTokens == null || evalDuration == null
                || evalDuration.isZero() ? null
                : completionTokens / (evalDuration.toNanos() / 1_000_000_000.0);
            String rawOutput = response.getResult() == null
                || response.getResult().getOutput() == null
                ? null : response.getResult().getOutput().getText();
            return new CallTelemetry(
                wallMillis,
                millis(duration(metadata, "total-duration")),
                millis(duration(metadata, "load-duration")),
                millis(duration(metadata, "prompt-eval-duration")),
                millis(evalDuration),
                usage == null ? null : usage.getPromptTokens(),
                completionTokens,
                tokensPerSecond,
                rawOutput,
                failure
            );
        }

        private static Duration duration(ChatResponseMetadata metadata, String key) {
            if (metadata == null) return null;
            Object value = metadata.get(key);
            return value instanceof Duration duration ? duration : null;
        }

        private static Long millis(Duration duration) {
            return duration == null ? null : duration.toMillis();
        }
    }
}
