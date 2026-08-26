package dev.codexofrealms.realm.domain;

public enum RealmRole {
    OWNER,
    EDITOR,
    PLAYER;

    public boolean canEditContent() {
        return this == OWNER || this == EDITOR;
    }

    public boolean canManageMemberships() {
        return this == OWNER;
    }
}
