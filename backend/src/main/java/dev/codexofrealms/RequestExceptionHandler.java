package dev.codexofrealms;

import dev.codexofrealms.shared.ApiProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class RequestExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ProblemDetail badRequest(Exception exception) {
        return ApiProblemDetails.create(
            HttpStatus.BAD_REQUEST,
            "Invalid request",
            "The request did not satisfy the API contract.",
            "request.invalid"
        );
    }
}
