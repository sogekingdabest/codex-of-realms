package dev.codexofrealms.realm.web;

import dev.codexofrealms.realm.application.DomainConflictException;
import dev.codexofrealms.realm.application.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail resourceNotFound() {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setTitle("Resource not found");
        detail.setDetail("The requested resource is unavailable.");
        return detail;
    }

    @ExceptionHandler(DomainConflictException.class)
    ProblemDetail domainConflict(DomainConflictException exception) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setTitle("Domain conflict");
        detail.setDetail(exception.getMessage());
        return detail;
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ProblemDetail badRequest(Exception exception) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Invalid request");
        detail.setDetail("The request did not satisfy the API contract.");
        return detail;
    }
}
