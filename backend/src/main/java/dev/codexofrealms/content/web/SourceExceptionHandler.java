package dev.codexofrealms.content.web;

import dev.codexofrealms.content.application.evidence.SourceEvidenceException;
import dev.codexofrealms.content.application.ingestion.IngestionException;
import dev.codexofrealms.content.application.source.SourceException;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
class SourceExceptionHandler {

    @ExceptionHandler(SourceException.class)
    ProblemDetail source(SourceException exception) {
        return problem(HttpStatus.NOT_FOUND, "Source unavailable", exception.getMessage(),
            exception.code().apiCode());
    }

    @ExceptionHandler(IngestionException.class)
    ProblemDetail ingestion(IngestionException exception) {
        return switch (exception.code()) {
            case INVALID_SOURCE -> problem(HttpStatus.BAD_REQUEST, "Invalid source",
                exception.getMessage(), exception.code().apiCode());
            case EMBEDDING_UNAVAILABLE -> problem(HttpStatus.SERVICE_UNAVAILABLE,
                "Embedding model unavailable", exception.getMessage(), exception.code().apiCode());
        };
    }

    @ExceptionHandler(SourceEvidenceException.class)
    ProblemDetail evidence(SourceEvidenceException exception) {
        return switch (exception.code()) {
            case UNAVAILABLE -> problem(HttpStatus.NOT_FOUND, "Source evidence unavailable",
                exception.getMessage(), exception.code().apiCode());
            case INVALID_SELECTION -> problem(HttpStatus.BAD_REQUEST, "Invalid source evidence",
                exception.getMessage(), exception.code().apiCode());
        };
    }

    @ExceptionHandler(IOException.class)
    ProblemDetail invalidMultipart() {
        return problem(HttpStatus.BAD_REQUEST, "Invalid source", "The upload could not be read.",
            "source.invalid");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail uploadTooLarge() {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "Source too large",
            "The upload exceeds the configured source limit.", "source.upload_too_large");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String message, String code) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(title);
        detail.setType(URI.create("urn:codex-of-realms:problem:" + code));
        detail.setProperty("code", code);
        return detail;
    }
}
