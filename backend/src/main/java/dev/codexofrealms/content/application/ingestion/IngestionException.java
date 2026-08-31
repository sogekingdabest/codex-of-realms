package dev.codexofrealms.content.application.ingestion;

public final class IngestionException extends RuntimeException {

    public enum Code {
        INVALID_SOURCE("source.invalid"),
        EMBEDDING_UNAVAILABLE("source.embedding_unavailable");

        private final String apiCode;

        Code(String apiCode) {
            this.apiCode = apiCode;
        }

        public String apiCode() {
            return apiCode;
        }
    }

    private final Code code;

    public IngestionException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    static IngestionException invalidSource(String message) {
        return new IngestionException(Code.INVALID_SOURCE, message);
    }

    public static IngestionException embeddingUnavailable(String message) {
        return new IngestionException(Code.EMBEDDING_UNAVAILABLE, message);
    }
}
