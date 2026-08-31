package dev.codexofrealms.qa.application.port;

public class AnswerModelUnavailableException extends RuntimeException {

    public AnswerModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public AnswerModelUnavailableException(String message) {
        super(message);
    }
}
