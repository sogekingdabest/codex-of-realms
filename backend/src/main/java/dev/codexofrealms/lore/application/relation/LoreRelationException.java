package dev.codexofrealms.lore.application.relation;

public final class LoreRelationException extends RuntimeException {

    public enum Code {
        UNAVAILABLE("lore_relation.unavailable"),
        ENDPOINT_UNAVAILABLE("lore_relation.endpoint_unavailable"),
        DUPLICATE("lore_relation.duplicate");

        private final String apiCode;

        Code(String apiCode) {
            this.apiCode = apiCode;
        }

        public String apiCode() {
            return apiCode;
        }
    }

    private final Code code;

    private LoreRelationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public static LoreRelationException unavailable() {
        return new LoreRelationException(Code.UNAVAILABLE, "The lore relation is unavailable.");
    }

    public static LoreRelationException endpointUnavailable() {
        return new LoreRelationException(
            Code.ENDPOINT_UNAVAILABLE,
            "One or more lore relation endpoints are unavailable."
        );
    }

    public static LoreRelationException duplicate() {
        return new LoreRelationException(
            Code.DUPLICATE,
            "That active lore relation already exists."
        );
    }
}
