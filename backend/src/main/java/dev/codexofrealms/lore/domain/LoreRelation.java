package dev.codexofrealms.lore.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record LoreRelation(
    UUID sourceEntityId,
    UUID targetEntityId,
    String relationType,
    String description
) {

    private static final Pattern TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public LoreRelation {
        Objects.requireNonNull(sourceEntityId, "sourceEntityId");
        Objects.requireNonNull(targetEntityId, "targetEntityId");
        if (sourceEntityId.equals(targetEntityId)) {
            throw new IllegalArgumentException("A lore relation requires two different entities.");
        }
        if (relationType == null || relationType.isBlank()) {
            throw new IllegalArgumentException("relationType is required.");
        }
        relationType = Normalizer.normalize(relationType.strip(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        if (!TYPE.matcher(relationType).matches()) {
            throw new IllegalArgumentException("relationType must be a valid controlled identifier.");
        }
        description = description == null || description.isBlank() ? "" : description.strip();
        if (description.length() > 2000) {
            throw new IllegalArgumentException("description is too long.");
        }
    }
}
