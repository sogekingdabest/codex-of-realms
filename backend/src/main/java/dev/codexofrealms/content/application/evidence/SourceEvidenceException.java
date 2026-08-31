package dev.codexofrealms.content.application.evidence;

public final class SourceEvidenceException extends RuntimeException {

    public enum Code {
        UNAVAILABLE("source_evidence.unavailable"),
        INVALID_SELECTION("source_evidence.invalid");

        private final String apiCode;

        Code(String apiCode) {
            this.apiCode = apiCode;
        }

        public String apiCode() {
            return apiCode;
        }
    }

    private final Code code;

    public SourceEvidenceException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public static SourceEvidenceException unavailable() {
        return new SourceEvidenceException(Code.UNAVAILABLE, "The requested source evidence is unavailable.");
    }

    public static SourceEvidenceException invalidSelection(String message) {
        return new SourceEvidenceException(Code.INVALID_SELECTION, message);
    }
}
