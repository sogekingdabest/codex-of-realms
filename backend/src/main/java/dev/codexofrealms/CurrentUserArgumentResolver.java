package dev.codexofrealms;

import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.CurrentUser;
import dev.codexofrealms.realm.RealmAccess;
import java.security.Principal;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

final class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final RealmAccess realmAccess;

    CurrentUserArgumentResolver(RealmAccess realmAccess) {
        this.realmAccess = realmAccess;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
            && parameter.getParameterType().equals(AuthenticatedUser.class);
    }

    @Override
    public AuthenticatedUser resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        Principal principal = webRequest.getUserPrincipal();
        if (!(principal instanceof JwtAuthenticationToken authentication)) {
            throw new AuthenticationCredentialsNotFoundException(
                "An authenticated JWT is required"
            );
        }

        Jwt jwt = authentication.getToken();
        String issuer = jwt.getClaimAsString("iss");
        String subject = jwt.getSubject();
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new AuthenticationCredentialsNotFoundException(
                "The authenticated JWT does not contain a valid external identity"
            );
        }

        try {
            return realmAccess.synchronizeIdentity(
                issuer,
                subject,
                firstPresent(
                    jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("preferred_username"),
                    subject
                ),
                jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaims().get("email_verified"))
            );
        } catch (IllegalArgumentException exception) {
            throw new AuthenticationCredentialsNotFoundException(
                "The authenticated JWT does not contain a valid external identity",
                exception
            );
        }
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
