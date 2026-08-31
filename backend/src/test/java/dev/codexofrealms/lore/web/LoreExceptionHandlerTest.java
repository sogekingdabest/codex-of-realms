package dev.codexofrealms.lore.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codexofrealms.lore.application.entity.LoreEntityException;
import dev.codexofrealms.lore.application.relation.LoreRelationException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class LoreExceptionHandlerTest {

    private final LoreExceptionHandler handler = new LoreExceptionHandler();

    @Test
    void mapsEveryEntityCodeToProblemDetails() {
        assertProblem(
            handler.entity(LoreEntityException.unavailable()),
            HttpStatus.NOT_FOUND,
            "lore_entity.unavailable"
        );
        assertProblem(
            handler.entity(LoreEntityException.activeRelations()),
            HttpStatus.CONFLICT,
            "lore_entity.active_relations"
        );
    }

    @Test
    void mapsEveryRelationCodeToProblemDetails() {
        assertProblem(
            handler.relation(LoreRelationException.unavailable()),
            HttpStatus.NOT_FOUND,
            "lore_relation.unavailable"
        );
        assertProblem(
            handler.relation(LoreRelationException.endpointUnavailable()),
            HttpStatus.NOT_FOUND,
            "lore_relation.endpoint_unavailable"
        );
        assertProblem(
            handler.relation(LoreRelationException.duplicate()),
            HttpStatus.CONFLICT,
            "lore_relation.duplicate"
        );
    }

    private static void assertProblem(
        ProblemDetail problem,
        HttpStatus expectedStatus,
        String expectedCode
    ) {
        assertThat(problem.getStatus()).isEqualTo(expectedStatus.value());
        assertThat(problem.getDetail()).isNotBlank();
        assertThat(problem.getTitle()).isNotBlank();
        assertThat(problem.getType()).isEqualTo(
            URI.create("urn:codex-of-realms:problem:" + expectedCode)
        );
        assertThat(problem.getProperties()).containsEntry("code", expectedCode);
    }
}
