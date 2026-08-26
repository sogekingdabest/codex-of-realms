package dev.codexofrealms.realm.application;

public final class DomainConflictException extends RuntimeException {

    public DomainConflictException(String message) {
        super(message);
    }
}
