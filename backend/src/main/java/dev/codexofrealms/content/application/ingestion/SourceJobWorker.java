package dev.codexofrealms.content.application.ingestion;

import dev.codexofrealms.content.*;
import dev.codexofrealms.content.application.port.*;

import io.micrometer.core.instrument.MeterRegistry;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(
        name = "codex.ingestion.worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SourceJobWorker {
    private final SourceJobRepository jobs;
    private final RawSourceStorage storage;
    private final SourceFileValidator validator;
    private final StructuralChunker chunker;
    private final ObjectProvider<TextEmbedding> embeddings;
    private final IngestionProperties properties;
    private final SourceJobCoordinator coordinator;
    private final MeterRegistry metrics;
    private final ExecutorService executor;
    private final ScheduledExecutorService heartbeats =
            Executors.newSingleThreadScheduledExecutor();
    private final Semaphore slots;

    SourceJobWorker(
            SourceJobRepository jobs,
            RawSourceStorage storage,
            SourceFileValidator validator,
            StructuralChunker chunker,
            ObjectProvider<TextEmbedding> embeddings,
            IngestionProperties properties,
            SourceJobCoordinator coordinator,
            MeterRegistry metrics,
            @Value("${codex.ingestion.worker-concurrency:1}") int concurrency) {
        if (concurrency < 1 || concurrency > 16)
            throw new IllegalArgumentException("Invalid worker concurrency");
        this.jobs = jobs;
        this.storage = storage;
        this.validator = validator;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.properties = properties;
        this.coordinator = coordinator;
        this.metrics = metrics;
        this.executor = Executors.newFixedThreadPool(concurrency);
        this.slots = new Semaphore(concurrency);
        metrics.gauge("codex.source.jobs.pending", jobs, SourceJobRepository::pendingCount);
    }

    @Scheduled(fixedDelayString = "${codex.ingestion.poll-milliseconds:2000}")
    public void poll() {
        jobs.recoverExpired();
        reconcile();
        while (slots.tryAcquire()) {
            Optional<SourceJob> job;
            try {
                job = jobs.claim();
            } catch (RuntimeException exception) {
                slots.release();
                throw exception;
            }
            if (job.isEmpty()) {
                slots.release();
                return;
            }
            try {
                executor.submit(
                        () -> {
                            try {
                                process(job.get());
                            } finally {
                                slots.release();
                            }
                        });
            } catch (RejectedExecutionException exception) {
                slots.release();
                return;
            }
        }
    }

    void reconcile() {
        for (SourceJob job : jobs.interruptedUploads()) {
            try {
                byte[] bytes = storage.read(job.version().storageKey());
                if (!SourceIngestionService.sha256(bytes).equals(job.version().checksum()))
                    throw new IllegalStateException();
                jobs.queue(job.view().id());
            } catch (RuntimeException exception) {
                jobs.uploadFailed(job.view().id(), "FILE_UNAVAILABLE");
            }
        }
    }

    void process(SourceJob job) {
        long start = System.nanoTime();
        AtomicBoolean owned = new AtomicBoolean(true);
        ScheduledFuture<?> heartbeat =
                heartbeats.scheduleAtFixedRate(
                        () -> {
                            try {
                                if (!jobs.renew(job)) owned.set(false);
                            } catch (RuntimeException exception) {
                                owned.set(false);
                            }
                        },
                        30,
                        30,
                        TimeUnit.SECONDS);
        String outcome = "failed";
        try {
            if (!job.pipelineConfig().equals(properties.toString()))
                throw new SourceJobException(
                        "PIPELINE_CHANGED", "Processing configuration changed");
            AcceptedSource source;
            try {
                byte[] bytes = storage.read(job.version().storageKey());
                if (!SourceIngestionService.sha256(bytes).equals(job.version().checksum()))
                    throw new IllegalStateException();
                source =
                        validator.validate(
                                bytes, job.version().originalFilename(), job.version().mediaType());
            } catch (RuntimeException exception) {
                throw new SourceJobException("FILE_UNAVAILABLE", "Original file unavailable");
            }
            List<SourceChunk> chunks = chunker.split(source.text());
            if (chunks.isEmpty())
                throw new SourceJobException("INVALID_SOURCE", "No usable chunks");
            if (!owned.get() || !jobs.progress(job, 0, chunks.size())) {
                outcome = "lease_lost";
                return;
            }
            TextEmbedding generator = embeddings.getIfAvailable();
            if (generator == null)
                throw new SourceJobException("PIPELINE_CHANGED", "Embedding model not configured");
            EmbeddingDescriptor descriptor = generator.descriptor();
            if (!descriptor.provider().equals(properties.embeddingProvider())
                    || !descriptor.model().equals(properties.embeddingModel()))
                throw new SourceJobException("PIPELINE_CHANGED", "Embedding configuration differs");
            List<float[]> vectors = new ArrayList<>();
            for (int offset = 0;
                    offset < chunks.size();
                    offset += properties.embeddingBatchSize()) {
                if (!owned.get() || Thread.currentThread().isInterrupted()) {
                    outcome = "lease_lost";
                    return;
                }
                int end = Math.min(chunks.size(), offset + properties.embeddingBatchSize());
                List<float[]> batch =
                        generator.embed(
                                chunks.subList(offset, end).stream()
                                        .map(SourceChunk::content)
                                        .toList());
                if (batch.size() != end - offset)
                    throw new SourceJobException("INVALID_EMBEDDINGS", "Embedding count differs");
                vectors.addAll(batch);
                if (!owned.get() || !jobs.progress(job, end, chunks.size())) {
                    outcome = "lease_lost";
                    return;
                }
            }
            if (owned.get())
                coordinator.activate(
                        job, chunks, vectors, descriptor.provider(), descriptor.model());
            outcome =
                    jobs.find(job.realmId(), job.view().id())
                            .map(
                                    value ->
                                            value.view().state() == SourceJobState.SUCCEEDED
                                                    ? "succeeded"
                                                    : "lease_lost")
                            .orElse("lease_lost");
        } catch (RuntimeException exception) {
            boolean transientFailure =
                    exception instanceof IngestionException ingestion
                            && ingestion.code() == IngestionException.Code.EMBEDDING_UNAVAILABLE;
            String code =
                    exception instanceof SourceJobException failure
                            ? failure.code()
                            : transientFailure ? "MODEL_UNAVAILABLE" : "PROCESSING_REJECTED";
            if (owned.get()) jobs.failed(job, code, transientFailure);
            if (transientFailure)
                metrics.counter("codex.source.jobs.retries", "reason", "model_unavailable")
                        .increment();
        } finally {
            heartbeat.cancel(false);
            metrics.timer("codex.source.jobs.duration", "outcome", outcome)
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            metrics.counter("codex.source.jobs.results", "outcome", outcome).increment();
        }
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
        heartbeats.shutdownNow();
    }
}
