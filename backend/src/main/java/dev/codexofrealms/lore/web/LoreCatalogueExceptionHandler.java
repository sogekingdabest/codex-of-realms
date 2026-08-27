package dev.codexofrealms.lore.web;

import dev.codexofrealms.lore.application.CatalogueConflictException;
import dev.codexofrealms.lore.application.CatalogueNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LoreCatalogueController.class)
class LoreCatalogueExceptionHandler {

    @ExceptionHandler(CatalogueNotFoundException.class)
    ProblemDetail notFound() {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setTitle("Resource not found");
        detail.setDetail("The requested resource is unavailable.");
        return detail;
    }

    @ExceptionHandler(CatalogueConflictException.class)
    ProblemDetail conflict(CatalogueConflictException exception) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setTitle("Catalogue conflict");
        detail.setDetail(exception.getMessage());
        return detail;
    }
}
