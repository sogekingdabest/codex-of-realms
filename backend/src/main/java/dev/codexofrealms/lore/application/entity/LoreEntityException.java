package dev.codexofrealms.lore.application.entity;

public final class LoreEntityException extends RuntimeException {

    public enum Code {
        UNAVAILABLE("lore_entity.unavailable"),
        ACTIVE_RELATIONS("lore_entity.active_relations");

        private final String apiCode;

        Code(String apiCode) {
            this.apiCode = apiCode;
        }

        public String apiCode() {
            return apiCode;
        }
    }

    private final Code code;

    private LoreEntityException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public static LoreEntityException unavailable() {
        return new LoreEntityException(Code.UNAVAILABLE, "The lore entity is unavailable.");
    }

    public static LoreEntityException activeRelations() {
        return new LoreEntityException(
            Code.ACTIVE_RELATIONS,
            "Delete the entity's active relations before deleting the entity."
        );
    }
}
