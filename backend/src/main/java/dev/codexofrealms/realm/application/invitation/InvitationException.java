package dev.codexofrealms.realm.application.invitation;

public final class InvitationException extends RuntimeException {

    public enum Code {
        NOT_FOUND("invitation.not_found"),
        INVALID_ROLE("invitation.invalid_role"),
        PENDING_EXISTS("invitation.pending_exists"),
        ALREADY_MEMBER("invitation.already_member");

        private final String apiCode;

        Code(String apiCode) {
            this.apiCode = apiCode;
        }

        public String apiCode() {
            return apiCode;
        }
    }

    private final Code code;

    public InvitationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
