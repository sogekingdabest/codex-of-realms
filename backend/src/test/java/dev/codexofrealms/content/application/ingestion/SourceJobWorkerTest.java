package dev.codexofrealms.content.application.ingestion;

import static dev.codexofrealms.content.application.ingestion.SourceJobFixtures.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.codexofrealms.content.*;
import dev.codexofrealms.content.application.port.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;

import java.util.*;

@SuppressWarnings("unchecked")
class SourceJobWorkerTest {
    final RawSourceStorage storage = mock(RawSourceStorage.class);
    final SourceJobRepository jobs = mock(SourceJobRepository.class);
    final SourceJobCoordinator coordinator = mock(SourceJobCoordinator.class);
    final ObjectProvider<TextEmbedding> provider = mock(ObjectProvider.class);
    final TextEmbedding embedding = mock(TextEmbedding.class);
    final SourceJobWorker worker =
            new SourceJobWorker(
                    jobs,
                    storage,
                    new SourceFileValidator(CONFIG),
                    new StructuralChunker(CONFIG),
                    provider,
                    CONFIG,
                    coordinator,
                    new SimpleMeterRegistry(),
                    1);

    @BeforeEach
    void setup() {
        when(storage.read(any())).thenReturn(BYTES);
        when(jobs.progress(any(), anyInt(), anyInt())).thenReturn(true);
        when(provider.getIfAvailable()).thenReturn(embedding);
        when(embedding.descriptor()).thenReturn(new EmbeddingDescriptor("test", "model"));
        when(embedding.embed(anyList()))
                .thenAnswer(
                        i ->
                                ((List<String>) i.getArgument(0))
                                        .stream().map(t -> new float[] {1, 0}).toList());
    }

    @AfterEach
    void close() {
        worker.close();
    }

    @Test
    void activationHappensOnlyAfterEveryBatch() {
        SourceJob job = job(SourceJobState.RUNNING);
        worker.process(job);
        verify(embedding, atLeast(2)).embed(anyList());
        verify(coordinator).activate(eq(job), anyList(), anyList(), eq("test"), eq("model"));
    }

    @Test
    void leaseLossAfterEmbeddingPreventsPublication() {
        SourceJob job = job(SourceJobState.RUNNING);
        when(jobs.progress(eq(job), anyInt(), anyInt())).thenReturn(true, false);
        worker.process(job);
        verify(embedding, times(1)).embed(anyList());
        verifyNoInteractions(coordinator);
        verify(jobs, never()).failed(any(), anyString(), anyBoolean());
    }

    @Test
    void transientModelFailureCanBeRetriedWithoutLeakingItsMessage() {
        SourceJob job = job(SourceJobState.RUNNING);
        when(embedding.embed(anyList()))
                .thenThrow(IngestionException.embeddingUnavailable("secret document content"));
        worker.process(job);
        verify(jobs).failed(job, "MODEL_UNAVAILABLE", true);
        verifyNoInteractions(coordinator);
    }

    @Test
    void missingFileIsPermanentAndDoesNotCallModel() {
        SourceJob job = job(SourceJobState.RUNNING);
        when(storage.read(any())).thenThrow(new IllegalStateException("secret path"));
        worker.process(job);
        verify(jobs).failed(job, "FILE_UNAVAILABLE", false);
        verifyNoInteractions(embedding, coordinator);
    }

    @Test
    void recoversCrashAfterAtomicWrite() {
        SourceJob job = job(SourceJobState.UPLOADING);
        when(jobs.interruptedUploads()).thenReturn(List.of(job));
        worker.reconcile();
        verify(jobs).queue(job.view().id());
    }

    @Test
    void crashBeforeWriteBecomesVisibleFailure() {
        SourceJob job = job(SourceJobState.UPLOADING);
        when(jobs.interruptedUploads()).thenReturn(List.of(job));
        when(storage.read(any())).thenThrow(new IllegalStateException());
        worker.reconcile();
        verify(jobs).uploadFailed(job.view().id(), "FILE_UNAVAILABLE");
    }
}
