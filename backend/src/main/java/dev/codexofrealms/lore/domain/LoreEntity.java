package dev.codexofrealms.lore.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record LoreEntity(
    EntityType type,
    String displayName,
    List<String> aliases,
    String description
) {

    public LoreEntity {
        Objects.requireNonNull(type, "type");
        displayName = requiredText(displayName, 160, "displayName");
        description = optionalText(description, 4000, "description");
        aliases = normalizeAliases(aliases, displayName);
    }

    private static List<String> normalizeAliases(List<String> requested, String displayName) {
        if (requested == null || requested.isEmpty()) return List.of();
        if (requested.size() > 20) {
            throw new IllegalArgumentException("aliases cannot contain more than 20 values.");
        }
        LinkedHashMap<String, String> distinct = new LinkedHashMap<>();
        String nameKey = displayName.toLowerCase(Locale.ROOT);
        for (String candidate : new ArrayList<>(requested)) {
            String alias = requiredText(candidate, 120, "alias");
            String key = alias.toLowerCase(Locale.ROOT);
            if (!key.equals(nameKey)) distinct.putIfAbsent(key, alias);
        }
        return List.copyOf(distinct.values());
    }

    private static String requiredText(String value, int maximum, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException(field + " is too long.");
        }
        return normalized;
    }

    private static String optionalText(String value, int maximum, String field) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.strip();
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException(field + " is too long.");
        }
        return normalized;
    }
}
