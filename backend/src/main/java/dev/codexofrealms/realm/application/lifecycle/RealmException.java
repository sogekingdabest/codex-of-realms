package dev.codexofrealms.realm.application.lifecycle;

public final class RealmException extends RuntimeException {

    public enum Code {
        UNAVAILABLE("realm.unavailable");

        private final String apiCode;

        Code(String apiCode) {
            this.apiCode = apiCode;
        }

        public String apiCode() {
            return apiCode;
        }
    }

    private final Code code;

    public RealmException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public static RealmException unavailable() {
        return new RealmException(Code.UNAVAILABLE, "The requested realm is unavailable.");
    }
}
