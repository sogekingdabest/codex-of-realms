package dev.codexofrealms.content.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codexofrealms.content.application.evidence.SourceEvidenceException;
import dev.codexofrealms.content.application.ingestion.IngestionException;
import dev.codexofrealms.content.application.source.SourceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class SourceExceptionHandlerTest {
    private final SourceExceptionHandler handler = new SourceExceptionHandler();

    @Test
    void mapsSourceUnavailable() {
        assertProblem(handler.source(SourceException.unavailable()), HttpStatus.NOT_FOUND, "source.unavailable");
    }

    @Test
    void mapsBothIngestionCodes() {
        assertProblem(handler.ingestion(new IngestionException(
            IngestionException.Code.INVALID_SOURCE, "invalid"
        )), HttpStatus.BAD_REQUEST, "source.invalid");
        assertProblem(handler.ingestion(IngestionException.embeddingUnavailable("unavailable")),
            HttpStatus.SERVICE_UNAVAILABLE, "source.embedding_unavailable");
    }

    @Test
    void mapsBothEvidenceCodes() {
        assertProblem(handler.evidence(SourceEvidenceException.unavailable()),
            HttpStatus.NOT_FOUND, "source_evidence.unavailable");
        assertProblem(handler.evidence(SourceEvidenceException.invalidSelection("invalid")),
            HttpStatus.BAD_REQUEST, "source_evidence.invalid");
    }

    @Test
    void mapsMultipartIoAndUploadLimit() {
        assertProblem(handler.invalidMultipart(), HttpStatus.BAD_REQUEST, "source.invalid");
        assertProblem(handler.uploadTooLarge(), HttpStatus.CONTENT_TOO_LARGE, "source.upload_too_large");
    }

    private static void assertProblem(ProblemDetail detail, HttpStatus status, String code) {
        assertThat(detail.getStatus()).isEqualTo(status.value());
        assertThat(detail.getProperties()).containsEntry("code", code);
        assertThat(detail.getType()).hasToString("urn:codex-of-realms:problem:" + code);
        assertThat(detail.getTitle()).isNotBlank();
        assertThat(detail.getDetail()).isNotBlank();
    }
}
