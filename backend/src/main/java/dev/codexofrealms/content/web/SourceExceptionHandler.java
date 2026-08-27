package dev.codexofrealms.content.web;

import dev.codexofrealms.content.application.EmbeddingUnavailableException;
import dev.codexofrealms.content.application.InvalidSourceException;
import dev.codexofrealms.content.application.SourceNotFoundException;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
class SourceExceptionHandler {

    @ExceptionHandler(SourceNotFoundException.class)
    ProblemDetail notFound() {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setTitle("Resource not found");
        detail.setDetail("The requested resource is unavailable.");
        return detail;
    }

    @ExceptionHandler({InvalidSourceException.class, IOException.class})
    ProblemDetail invalidSource(Exception exception) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Invalid source");
        detail.setDetail(exception instanceof InvalidSourceException ? exception.getMessage() : "The upload could not be read.");
        return detail;
    }

    @ExceptionHandler(EmbeddingUnavailableException.class)
    ProblemDetail embeddingUnavailable() {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        detail.setTitle("Embedding model unavailable");
        detail.setDetail("Configure and start the local embedding model before processing sources.");
        return detail;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail uploadTooLarge() {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONTENT_TOO_LARGE);
        detail.setTitle("Source too large");
        detail.setDetail("The upload exceeds the configured source limit.");
        return detail;
    }
}
