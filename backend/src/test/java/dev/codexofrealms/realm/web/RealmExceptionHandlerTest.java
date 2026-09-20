package dev.codexofrealms.realm.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codexofrealms.realm.application.access.AccessPolicyException;
import dev.codexofrealms.realm.application.invitation.InvitationException;
import dev.codexofrealms.realm.application.lifecycle.RealmException;
import dev.codexofrealms.realm.application.membership.MembershipException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class RealmExceptionHandlerTest {

    private final RealmExceptionHandler handler = new RealmExceptionHandler();

    @ParameterizedTest
    @MethodSource("applicationErrors")
    void mapsApplicationErrorsToStableProblemDetails(
        RuntimeException exception,
        HttpStatus status,
        String code
    ) {
        ProblemDetail problem = map(exception);

        assertThat(problem.getStatus()).isEqualTo(status.value());
        assertThat(problem.getProperties()).containsExactlyEntriesOf(Map.of("code", code));
        assertThat(problem.getType().toString())
            .isEqualTo("urn:codex-of-realms:problem:" + code);
        assertThat(problem.getTitle()).isNotBlank();
        assertThat(problem.getDetail()).isNotBlank();
    }

    private ProblemDetail map(RuntimeException exception) {
        if (exception instanceof RealmException realm) {
            return handler.realm(realm);
        }
        if (exception instanceof MembershipException membership) {
            return handler.membership(membership);
        }
        if (exception instanceof InvitationException invitation) {
            return handler.invitation(invitation);
        }
        if (exception instanceof AccessPolicyException accessPolicy) {
            return handler.accessPolicy(accessPolicy);
        }
        throw new IllegalArgumentException("Unsupported exception", exception);
    }

    private static Stream<Arguments> applicationErrors() {
        Stream<Arguments> realms = Stream.of(Arguments.of(
            RealmException.unavailable(), HttpStatus.NOT_FOUND, "realm.unavailable"
        ));
        Stream<Arguments> memberships = Stream.of(MembershipException.Code.values())
            .map(code -> Arguments.of(
                new MembershipException(code, "membership"),
                code == MembershipException.Code.LAST_OWNER
                    ? HttpStatus.CONFLICT
                    : HttpStatus.NOT_FOUND,
                code.apiCode()
            ));
        Stream<Arguments> invitations = Stream.of(InvitationException.Code.values())
            .map(code -> Arguments.of(
                new InvitationException(code, "invitation"),
                code == InvitationException.Code.NOT_FOUND
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.CONFLICT,
                code.apiCode()
            ));
        Stream<Arguments> policies = Stream.of(AccessPolicyException.Code.values())
            .map(code -> Arguments.of(
                new AccessPolicyException(code, "policy"),
                code == AccessPolicyException.Code.UNAVAILABLE
                    || code == AccessPolicyException.Code.GRANTEE_UNAVAILABLE
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.CONFLICT,
                code.apiCode()
            ));
        return Stream.of(realms, memberships, invitations, policies).flatMap(stream -> stream);
    }
}
