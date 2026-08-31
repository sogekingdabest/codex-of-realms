package dev.codexofrealms.realm.application.access;

public final class AccessPolicyException extends RuntimeException {

    public enum Code {
        UNAVAILABLE("access_policy.unavailable"),
        GRANTEE_UNAVAILABLE("access_policy.grantee_unavailable"),
        GRANTS_UNSUPPORTED("access_policy.grants_unsupported"),
        INVALID_GRANTEE("access_policy.invalid_grantee"),
        NAME_EXHAUSTED("access_policy.name_exhausted");

        private final String apiCode;

        Code(String apiCode) {
            this.apiCode = apiCode;
        }

        public String apiCode() {
            return apiCode;
        }
    }

    private final Code code;

    public AccessPolicyException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public static AccessPolicyException unavailable() {
        return new AccessPolicyException(
            Code.UNAVAILABLE,
            "The requested access policy is unavailable."
        );
    }
}
