package dev.codexofrealms.content.application.source;

public final class SourceException extends RuntimeException {

    public enum Code {
        UNAVAILABLE("source.unavailable");

        private final String apiCode;

        Code(String apiCode) {
            this.apiCode = apiCode;
        }

        public String apiCode() {
            return apiCode;
        }
    }

    private final Code code;

    public SourceException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public static SourceException unavailable() {
        return new SourceException(Code.UNAVAILABLE, "The requested source is unavailable.");
    }
}
