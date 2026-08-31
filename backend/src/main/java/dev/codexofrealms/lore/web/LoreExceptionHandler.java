package dev.codexofrealms.lore.web;

import dev.codexofrealms.lore.application.entity.LoreEntityException;
import dev.codexofrealms.lore.application.relation.LoreRelationException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LoreCatalogueController.class)
class LoreExceptionHandler {

    @ExceptionHandler(LoreEntityException.class)
    ProblemDetail entity(LoreEntityException exception) {
        HttpStatus status = exception.code() == LoreEntityException.Code.ACTIVE_RELATIONS
            ? HttpStatus.CONFLICT
            : HttpStatus.NOT_FOUND;
        return problem(
            status,
            status == HttpStatus.CONFLICT ? "Lore entity conflict" : "Lore entity unavailable",
            exception.getMessage(),
            exception.code().apiCode()
        );
    }

    @ExceptionHandler(LoreRelationException.class)
    ProblemDetail relation(LoreRelationException exception) {
        HttpStatus status = exception.code() == LoreRelationException.Code.DUPLICATE
            ? HttpStatus.CONFLICT
            : HttpStatus.NOT_FOUND;
        return problem(
            status,
            status == HttpStatus.CONFLICT ? "Lore relation conflict" : "Lore relation unavailable",
            exception.getMessage(),
            exception.code().apiCode()
        );
    }

    private static ProblemDetail problem(
        HttpStatus status,
        String title,
        String message,
        String code
    ) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(title);
        detail.setType(URI.create("urn:codex-of-realms:problem:" + code));
        detail.setProperty("code", code);
        return detail;
    }
}
