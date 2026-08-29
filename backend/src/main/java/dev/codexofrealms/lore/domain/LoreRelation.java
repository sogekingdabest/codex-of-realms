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
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}++");
    private static final Pattern NON_IDENTIFIER_CHARACTERS = Pattern.compile("[^A-Z0-9]++");

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
            .toUpperCase(Locale.ROOT);
        relationType = DIACRITICS.matcher(relationType).replaceAll("");
        relationType = NON_IDENTIFIER_CHARACTERS.matcher(relationType).replaceAll("_");
        relationType = trimUnderscores(relationType);
        if (!TYPE.matcher(relationType).matches()) {
            throw new IllegalArgumentException("relationType must be a valid controlled identifier.");
        }
        description = description == null || description.isBlank() ? "" : description.strip();
        if (description.length() > 2000) {
            throw new IllegalArgumentException("description is too long.");
        }
    }

    private static String trimUnderscores(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '_') start++;
        while (end > start && value.charAt(end - 1) == '_') end--;
        return value.substring(start, end);
    }
}
