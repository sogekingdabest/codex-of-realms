package dev.codexofrealms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;

class RequestExceptionHandlerTest {

    private final RequestExceptionHandler handler = new RequestExceptionHandler();

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void mapsSupportedValidationErrorsToTheStableContract(Exception exception) {
        ProblemDetail problem = handler.badRequest(exception);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Invalid request");
        assertThat(problem.getDetail()).isEqualTo(
            "The request did not satisfy the API contract."
        );
        assertThat(problem.getType()).isEqualTo(
            URI.create("urn:codex-of-realms:problem:request.invalid")
        );
        assertThat(problem.getProperties()).containsExactlyEntriesOf(
            Map.of("code", "request.invalid")
        );
    }

    private static Stream<Exception> invalidRequests() {
        return Stream.of(
            new IllegalArgumentException("invalid"),
            mock(MethodArgumentNotValidException.class)
        );
    }
}
