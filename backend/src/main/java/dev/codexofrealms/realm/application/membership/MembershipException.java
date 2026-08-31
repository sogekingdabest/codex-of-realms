package dev.codexofrealms.realm.application.membership;

public final class MembershipException extends RuntimeException {

    public enum Code {
        NOT_FOUND("membership.not_found"),
        TARGET_USER_UNAVAILABLE("membership.target_user_unavailable"),
        LAST_OWNER("membership.last_owner");

        private final String apiCode;

        Code(String apiCode) {
            this.apiCode = apiCode;
        }

        public String apiCode() {
            return apiCode;
        }
    }

    private final Code code;

    public MembershipException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
