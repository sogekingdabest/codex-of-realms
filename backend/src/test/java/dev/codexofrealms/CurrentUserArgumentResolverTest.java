package dev.codexofrealms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.CurrentUser;
import dev.codexofrealms.realm.RealmAccess;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.NativeWebRequest;

class CurrentUserArgumentResolverTest {

    private static final String ISSUER = "https://identity.example.test/realms/codex";
    private static final String SUBJECT = "player-42";

    private final RealmAccess realmAccess = mock(RealmAccess.class);
    private final CurrentUserArgumentResolver resolver =
        new CurrentUserArgumentResolver(realmAccess);

    @Test
    void supportsOnlyAnnotatedAuthenticatedUserParameters() throws Exception {
        assertThat(resolver.supportsParameter(parameter("annotated", AuthenticatedUser.class)))
            .isTrue();
        assertThat(resolver.supportsParameter(parameter("plain", AuthenticatedUser.class)))
            .isFalse();
        assertThat(resolver.supportsParameter(parameter("wrongType", UUID.class)))
            .isFalse();
    }

    @Test
    void synchronizesTheJwtIdentityAndPrefersName() {
        AuthenticatedUser expected = user("Display name", "player@example.test");
        NativeWebRequest request = request(jwtBuilder()
            .claim("name", "Display name")
            .claim("preferred_username", "player")
            .claim("email", "player@example.test")
            .build());
        when(realmAccess.synchronizeIdentity(
            ISSUER, SUBJECT, "Display name", "player@example.test", false
        )).thenReturn(expected);

        assertThat(resolver.resolveArgument(null, null, request, null)).isEqualTo(expected);

        verify(realmAccess).synchronizeIdentity(
            ISSUER, SUBJECT, "Display name", "player@example.test", false
        );
    }

    @Test
    void fallsBackToPreferredUsernameAndAllowsMissingEmail() {
        AuthenticatedUser expected = user("player", null);
        NativeWebRequest request = request(jwtBuilder()
            .claim("name", "  ")
            .claim("preferred_username", "player")
            .build());
        when(realmAccess.synchronizeIdentity(ISSUER, SUBJECT, "player", null, false))
            .thenReturn(expected);

        assertThat(resolver.resolveArgument(null, null, request, null)).isEqualTo(expected);
    }

    @Test
    void fallsBackToSubjectWhenDisplayNameClaimsAreMissing() {
        AuthenticatedUser expected = user(SUBJECT, null);
        NativeWebRequest request = request(jwtBuilder().build());
        when(realmAccess.synchronizeIdentity(ISSUER, SUBJECT, SUBJECT, null, false))
            .thenReturn(expected);

        assertThat(resolver.resolveArgument(null, null, request, null)).isEqualTo(expected);
    }

    @Test
    void rejectsMissingOrNonJwtPrincipals() {
        NativeWebRequest missing = mock(NativeWebRequest.class);
        NativeWebRequest nonJwt = mock(NativeWebRequest.class);
        when(nonJwt.getUserPrincipal()).thenReturn(mock(Principal.class));

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, missing, null))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, nonJwt, null))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verify(realmAccess, never()).synchronizeIdentity(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyBoolean()
        );
    }

    @Test
    void rejectsJwtWithoutIssuerBeforeSynchronizing() {
        NativeWebRequest request = request(Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(SUBJECT)
            .build());

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, request, null))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verify(realmAccess, never()).synchronizeIdentity(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyBoolean()
        );
    }

    private static NativeWebRequest request(Jwt jwt) {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getUserPrincipal()).thenReturn(new JwtAuthenticationToken(jwt));
        return request;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"true", "false", "1"})
    void doesNotCoerceVerificationStrings(String value) {
        resolver.resolveArgument(null, null, request(jwtBuilder()
            .claim("email", "player@example.test").claim("email_verified", value).build()), null);
        verify(realmAccess).synchronizeIdentity(ISSUER, SUBJECT, SUBJECT, "player@example.test", false);
    }

    @Test
    void passesOnlyVerifiedBooleanAsVerified() {
        resolver.resolveArgument(null, null, request(jwtBuilder()
            .claim("email", "player@example.test").claim("email_verified", true).build()), null);
        verify(realmAccess).synchronizeIdentity(ISSUER, SUBJECT, SUBJECT, "player@example.test", true);
    }

    @Test
    void passesFalseBooleanAsUnverified() {
        resolver.resolveArgument(null, null, request(jwtBuilder()
            .claim("email", "player@example.test").claim("email_verified", false).build()), null);
        verify(realmAccess).synchronizeIdentity(ISSUER, SUBJECT, SUBJECT, "player@example.test", false);
    }

    private static Jwt.Builder jwtBuilder() {
        return Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("iss", ISSUER)
            .subject(SUBJECT);
    }

    private static AuthenticatedUser user(String displayName, String email) {
        return new AuthenticatedUser(
            UUID.randomUUID(), ISSUER, SUBJECT, displayName, email
        );
    }

    private static MethodParameter parameter(String methodName, Class<?> parameterType)
        throws Exception {
        Method method = Parameters.class.getDeclaredMethod(methodName, parameterType);
        return new MethodParameter(method, 0);
    }

    private static final class Parameters {

        void annotated(@CurrentUser AuthenticatedUser user) {
        }

        void plain(AuthenticatedUser user) {
        }

        void wrongType(@CurrentUser UUID userId) {
        }
    }
}
