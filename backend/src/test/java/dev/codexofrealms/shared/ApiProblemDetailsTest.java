package dev.codexofrealms.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ApiProblemDetailsTest {

    @Test
    void createsTheCanonicalProblemDetailsContract() {
        ProblemDetail problem = ApiProblemDetails.create(
            HttpStatus.CONFLICT,
            "Conflict title",
            "Conflict detail.",
            "test.conflict"
        );

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Conflict title");
        assertThat(problem.getDetail()).isEqualTo("Conflict detail.");
        assertThat(problem.getType()).isEqualTo(
            URI.create("urn:codex-of-realms:problem:test.conflict")
        );
        assertThat(problem.getProperties()).containsExactlyEntriesOf(
            Map.of("code", "test.conflict")
        );
    }
}
