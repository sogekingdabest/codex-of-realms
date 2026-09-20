package dev.codexofrealms.content.application.ingestion;

import static dev.codexofrealms.content.application.ingestion.SourceJobFixtures.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.codexofrealms.content.application.port.*;
import dev.codexofrealms.realm.RealmAccess;

import org.junit.jupiter.api.Test;

import java.util.*;

class SourceIngestionServiceTest {
    final RawSourceStorage storage = mock(RawSourceStorage.class);
    final SourceJobRepository jobs = mock(SourceJobRepository.class);
    final SourceJobCoordinator coordinator = mock(SourceJobCoordinator.class);
    final RealmAccess access = mock(RealmAccess.class);
    final SourceIngestionService service =
            new SourceIngestionService(
                    new SourceFileValidator(CONFIG), storage, coordinator, jobs, CONFIG, access);

    void prepare(SourceJob job, boolean write) {
        when(coordinator.prepare(
                        eq(REALM),
                        isNull(),
                        eq(USER),
                        eq("Crónica"),
                        eq(POLICY),
                        any(),
                        eq("key-1"),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenReturn(new SourceJobCoordinator.Prepared(job, write, null));
        when(jobs.find(REALM, job.view().id())).thenReturn(Optional.of(job));
        when(jobs.queue(job.view().id())).thenReturn(true);
    }

    SourceSubmission upload() {
        return service.create(
                REALM, USER, "Crónica", POLICY, BYTES, "cronica.md", "text/markdown", "key-1");
    }

    @Test
    void recordsOperationBeforeWritingAndQueuesOnlyAfterAtomicWrite() {
        SourceJob job = job(SourceJobState.UPLOADING);
        prepare(job, true);
        assertThat(upload().documentId()).isEqualTo(job.version().documentId());
        var order = inOrder(coordinator, storage, jobs);
        order.verify(coordinator)
                .prepare(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        order.verify(storage).write(job.version().storageKey(), BYTES);
        order.verify(jobs).queue(job.view().id());
    }

    @Test
    void preservesOriginalWhenAnotherRequestAlreadyQueuedTheJob() {
        SourceJob job = job(SourceJobState.UPLOADING);
        prepare(job, true);
        when(jobs.queue(job.view().id())).thenReturn(false);
        upload();
        verify(storage).write(job.version().storageKey(), BYTES);
        verify(storage, never()).delete(any());
    }

    @Test
    void duplicateSubmissionNeverRewritesTheOriginal() {
        SourceJob job = job(SourceJobState.QUEUED);
        prepare(job, false);
        assertThat(upload().job().id()).isEqualTo(job.view().id());
        verifyNoInteractions(storage);
    }

    @Test
    void diskFailureIsRecordedAndNeverQueued() {
        SourceJob job = job(SourceJobState.UPLOADING);
        prepare(job, true);
        doThrow(new IllegalStateException("private path")).when(storage).write(any(), any());
        assertThatThrownBy(this::upload)
                .isInstanceOf(SourceJobException.class)
                .hasMessageNotContaining("private path");
        verify(jobs).uploadFailed(job.view().id(), "FILE_UNAVAILABLE");
        verify(jobs, never()).queue(any());
    }

    @Test
    void missingFileNeedsReplacementOnTheSameDocument() {
        SourceJob job = job(SourceJobState.FAILED);
        when(jobs.find(REALM, job.view().id())).thenReturn(Optional.of(job));
        when(storage.read(any())).thenThrow(new IllegalStateException());
        assertThatThrownBy(() -> service.retry(REALM, job.view().id(), USER))
                .isInstanceOf(SourceJobException.class);
        verifyNoInteractions(coordinator);
    }

    @Test
    void checksAdministrativeAccessBeforeReadingJobs() {
        doThrow(new IllegalStateException()).when(access).requireEditor(REALM, USER);
        assertThatThrownBy(() -> service.list(REALM, USER))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jobs, storage);
    }
}
