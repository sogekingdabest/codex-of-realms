package dev.codexofrealms.realm.web;

import dev.codexofrealms.realm.application.identity.ExternalIdentity;
import org.springframework.security.oauth2.jwt.Jwt;

final class OidcIdentityMapper {

    private OidcIdentityMapper() {
    }

    static ExternalIdentity from(Jwt jwt) {
        String issuer = jwt.getClaimAsString("iss");
        String subject = jwt.getSubject();
        String displayName = firstPresent(
            jwt.getClaimAsString("name"),
            jwt.getClaimAsString("preferred_username"),
            subject
        );

        return new ExternalIdentity(
            issuer,
            subject,
            displayName,
            jwt.getClaimAsString("email")
        );
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
