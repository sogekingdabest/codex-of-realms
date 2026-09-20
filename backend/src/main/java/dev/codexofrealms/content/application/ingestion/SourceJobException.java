package dev.codexofrealms.content.application.ingestion;

public final class SourceJobException extends RuntimeException {
    private final String code;

    public SourceJobException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
