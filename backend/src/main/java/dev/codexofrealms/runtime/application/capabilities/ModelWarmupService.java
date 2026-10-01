package dev.codexofrealms.runtime.application.capabilities;

import dev.codexofrealms.runtime.ModelWarmup;
import dev.codexofrealms.runtime.ModelWarmupState;
import dev.codexofrealms.runtime.application.port.ModelPreloader;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Loads the configured models in the background, at startup and when a member is about to ask,
 * so that a question does not wait for a cold model.
 */
@Service
public class ModelWarmupService {

    private static final Logger log = LoggerFactory.getLogger(ModelWarmupService.class);

    private final ModelPreloader preloader;
    private final RuntimeModelConfiguration configuration;
    private final String chatKeepAlive;
    private final Executor executor;
    private final AtomicBoolean loading = new AtomicBoolean();

    @Autowired
    public ModelWarmupService(
        ModelPreloader preloader,
        RuntimeModelConfiguration configuration,
        @Value("${codex.qa.chat-keep-alive:5m}") String chatKeepAlive
    ) {
        this(preloader, configuration, chatKeepAlive, task -> Thread.ofVirtual().name("model-warm-up").start(task));
    }

    ModelWarmupService(
        ModelPreloader preloader,
        RuntimeModelConfiguration configuration,
        String chatKeepAlive,
        Executor executor
    ) {
        this.preloader = preloader;
        this.configuration = configuration;
        this.chatKeepAlive = chatKeepAlive;
        this.executor = executor;
    }

    public ModelWarmup warmUp() {
        boolean embedding = configuration.embedding().usesOllama();
        boolean chat = configuration.chat().usesOllama();
        if (!embedding && !chat) {
            return new ModelWarmup(ModelWarmupState.NOT_CONFIGURED);
        }
        if (!loading.compareAndSet(false, true)) {
            return new ModelWarmup(ModelWarmupState.ALREADY_LOADING);
        }
        try {
            executor.execute(() -> {
                try {
                    // A question embeds first, so its model loads first.
                    if (embedding) load("embedding", () -> preloader.loadEmbedding(configuration.embedding().model()));
                    if (chat) load("chat", () -> preloader.loadChat(configuration.chat().model(), chatKeepAlive));
                } finally {
                    loading.set(false);
                }
            });
        } catch (RuntimeException rejected) {
            loading.set(false);
            throw rejected;
        }
        return new ModelWarmup(ModelWarmupState.STARTED);
    }

    @EventListener(ApplicationReadyEvent.class)
    void warmUpOnStartup() {
        warmUp();
    }

    private static void load(String kind, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            // A failed warm-up only means that the next request pays the load time.
            log.info("Could not preload the {} model: {}", kind, exception.getMessage());
        }
    }
}
