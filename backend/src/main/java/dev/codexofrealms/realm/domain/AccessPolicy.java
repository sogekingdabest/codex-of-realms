package dev.codexofrealms.realm.domain;

import java.util.Objects;

public record AccessPolicy(AccessClassification classification) {

    public AccessPolicy {
        Objects.requireNonNull(classification, "classification");
    }

    public boolean allows(RealmRole role, boolean explicitlyGranted) {
        Objects.requireNonNull(role, "role");

        if (role.canEditContent()) {
            return true;
        }

        return switch (classification) {
            case PUBLIC -> true;
            case GM_ONLY -> false;
            case SPOILER -> explicitlyGranted;
        };
    }

    public boolean supportsExplicitGrants() {
        return classification == AccessClassification.SPOILER;
    }
}
