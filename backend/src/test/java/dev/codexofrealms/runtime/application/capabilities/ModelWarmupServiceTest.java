package dev.codexofrealms.runtime.application.capabilities;

import static dev.codexofrealms.runtime.ModelWarmupState.ALREADY_LOADING;
import static dev.codexofrealms.runtime.ModelWarmupState.NOT_CONFIGURED;
import static dev.codexofrealms.runtime.ModelWarmupState.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import dev.codexofrealms.runtime.application.port.ModelPreloader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

class ModelWarmupServiceTest {

    private final RecordingPreloader preloader = new RecordingPreloader();

    @Test
    void loadsTheEmbeddingModelBeforeTheChatModelWithTheAnswerOptions() {
        ModelWarmupService service = service("ollama", "qwen3.5:4b", "ollama", "bge-m3", Runnable::run);

        assertThat(service.warmUp().state()).isEqualTo(STARTED);
        assertThat(preloader.calls).containsExactly("embedding bge-m3", "chat qwen3.5:4b 30m 12288");
    }

    @Test
    void loadsOnlyTheModelsServedByOllama() {
        ModelWarmupService service = service("none", "", "ollama", "bge-m3", Runnable::run);

        assertThat(service.warmUp().state()).isEqualTo(STARTED);
        assertThat(preloader.calls).containsExactly("embedding bge-m3");
    }

    @Test
    void doesNothingWithoutAnOllamaModel() {
        ModelWarmupService service = service("none", "", "none", "", task -> {
            throw new AssertionError("Nothing must be scheduled without an Ollama model.");
        });

        assertThat(service.warmUp().state()).isEqualTo(NOT_CONFIGURED);
        assertThat(preloader.calls).isEmpty();
    }

    @Test
    void startsOneLoadAtATime() {
        List<Runnable> scheduled = new ArrayList<>();
        ModelWarmupService service = service("ollama", "qwen3.5:4b", "ollama", "bge-m3", scheduled::add);

        assertThat(service.warmUp().state()).isEqualTo(STARTED);
        assertThat(service.warmUp().state()).isEqualTo(ALREADY_LOADING);
        assertThat(scheduled).hasSize(1);

        scheduled.getFirst().run();

        assertThat(service.warmUp().state()).isEqualTo(STARTED);
    }

    @Test
    void keepsLoadingAfterAFailureAndAllowsANewAttempt() {
        preloader.failEmbedding = true;
        ModelWarmupService service = service("ollama", "qwen3.5:4b", "ollama", "bge-m3", Runnable::run);

        assertThat(service.warmUp().state()).isEqualTo(STARTED);
        assertThat(preloader.calls).containsExactly("embedding bge-m3", "chat qwen3.5:4b 30m 12288");
        assertThat(service.warmUp().state()).isEqualTo(STARTED);
    }

    private ModelWarmupService service(
        String chatProvider,
        String chatModel,
        String embeddingProvider,
        String embeddingModel,
        Executor executor
    ) {
        return new ModelWarmupService(
            preloader,
            RuntimeModelConfiguration.of(chatProvider, chatModel, embeddingProvider, embeddingModel),
            "30m",
            12288,
            executor
        );
    }

    private static final class RecordingPreloader implements ModelPreloader {

        private final List<String> calls = new ArrayList<>();
        private boolean failEmbedding;

        @Override
        public void loadEmbedding(String model) {
            calls.add("embedding " + model);
            if (failEmbedding) {
                throw new IllegalStateException("connection refused");
            }
        }

        @Override
        public void loadChat(String model, String keepAlive, int contextSize) {
            calls.add("chat " + model + " " + keepAlive + " " + contextSize);
        }
    }
}
