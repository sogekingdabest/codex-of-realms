package dev.codexofrealms.qa.application;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class TextTerms {

    private static final Set<String> STOP_WORDS = Set.of(
        "como", "cual", "cuando", "donde", "este", "esta", "estos", "estas",
        "hacia", "hasta", "para", "pero", "porque", "quien", "sobre", "tiene",
        "tienen", "todo", "toda", "todos", "todas", "unas", "unos", "ahora"
    );

    private TextTerms() {
    }

    static Set<String> significant(String text) {
        if (text == null || text.isBlank()) return Set.of();
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replaceAll("[^a-z0-9]+", " ")
            .strip();
        if (normalized.isEmpty()) return Set.of();
        return Arrays.stream(normalized.split("\\s+"))
            .filter(token -> token.matches("\\d+") || token.length() >= 4)
            .filter(token -> !STOP_WORDS.contains(token))
            .map(TextTerms::stem)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static double coverage(String subject, String supportingText) {
        Set<String> requested = significant(subject);
        if (requested.isEmpty()) return 0.0;
        Set<String> available = significant(supportingText);
        long matches = requested.stream().filter(available::contains).count();
        return (double) matches / requested.size();
    }

    private static String stem(String token) {
        if (token.matches("\\d+")) return token;
        if (token.length() >= 7) return token.substring(0, 5);
        if (token.length() > 4 && token.endsWith("s")) return token.substring(0, token.length() - 1);
        return token;
    }
}
