package dev.codexofrealms.content.web;

import dev.codexofrealms.content.application.evidence.SourceEvidenceException;
import dev.codexofrealms.content.application.ingestion.IngestionException;
import dev.codexofrealms.content.application.source.SourceException;
import dev.codexofrealms.shared.ApiProblemDetails;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
class SourceExceptionHandler {

    @ExceptionHandler(dev.codexofrealms.content.application.ingestion.SourceJobException.class)
    ProblemDetail job(dev.codexofrealms.content.application.ingestion.SourceJobException exception) {
        return ApiProblemDetails.create(exception.code().equals("source.upload_in_progress") ? HttpStatus.TOO_EARLY : HttpStatus.CONFLICT, "Source operation needs attention", exception.getMessage(),exception.code());
    }

    @ExceptionHandler(SourceException.class)
    ProblemDetail source(SourceException exception) {
        return ApiProblemDetails.create(HttpStatus.NOT_FOUND, "Source unavailable", exception.getMessage(),
            exception.code().apiCode());
    }

    @ExceptionHandler(IngestionException.class)
    ProblemDetail ingestion(IngestionException exception) {
        return switch (exception.code()) {
            case INVALID_SOURCE -> ApiProblemDetails.create(HttpStatus.BAD_REQUEST, "Invalid source",
                exception.getMessage(), exception.code().apiCode());
            case EMBEDDING_UNAVAILABLE -> ApiProblemDetails.create(HttpStatus.SERVICE_UNAVAILABLE,
                "Embedding model unavailable", exception.getMessage(), exception.code().apiCode());
        };
    }

    @ExceptionHandler(SourceEvidenceException.class)
    ProblemDetail evidence(SourceEvidenceException exception) {
        return switch (exception.code()) {
            case UNAVAILABLE -> ApiProblemDetails.create(HttpStatus.NOT_FOUND, "Source evidence unavailable",
                exception.getMessage(), exception.code().apiCode());
            case INVALID_SELECTION -> ApiProblemDetails.create(HttpStatus.BAD_REQUEST, "Invalid source evidence",
                exception.getMessage(), exception.code().apiCode());
        };
    }

    @ExceptionHandler(IOException.class)
    ProblemDetail invalidMultipart() {
        return ApiProblemDetails.create(HttpStatus.BAD_REQUEST, "Invalid source", "The upload could not be read.",
            "source.invalid");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail uploadTooLarge() {
        return ApiProblemDetails.create(HttpStatus.CONTENT_TOO_LARGE, "Source too large",
            "The upload exceeds the configured source limit.", "source.upload_too_large");
    }
}
