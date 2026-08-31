package dev.codexofrealms.realm.application.identity;

import java.util.Objects;

public record ExternalIdentity(
    String issuer,
    String subject,
    String displayName,
    String email
) {

    public ExternalIdentity {
        issuer = requireBounded("issuer", issuer, 512);
        subject = requireBounded("subject", subject, 255);
        displayName = normalizeOptional(displayName, subject, 160);
        email = normalizeOptional(email, null, 320);
    }

    private static String requireBounded(String field, String value, int maximumLength) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is missing or too long");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, String fallback, int maximumLength) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        String normalized = value.trim();
        return normalized.length() <= maximumLength
            ? normalized
            : normalized.substring(0, maximumLength);
    }
}
