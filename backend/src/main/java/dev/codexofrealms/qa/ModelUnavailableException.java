package dev.codexofrealms.qa;

public class ModelUnavailableException extends RuntimeException {

    public ModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ModelUnavailableException(String message) {
        super(message);
    }
}
