package dev.codexofrealms.realm.application;

public final class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException() {
        super("Resource not found");
    }
}
