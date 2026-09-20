package dev.codexofrealms.lore;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Query relevance only. Evaluation fact scoring deliberately keeps its own frozen implementation. */
public final class PassageRelevance {
    private PassageRelevance() {}
    private static final Set<String> STOP = Set.of("como", "cual", "cuando", "donde", "este", "esta",
        "estos", "estas", "hacia", "hasta", "para", "pero", "porque", "quien", "sobre", "tiene",
        "tienen", "todo", "toda", "todos", "todas", "unas", "unos", "ahora");
    private static Set<String> terms(String text) {
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9]+", " ").strip();
        return Arrays.stream(normalized.split("\\s+"))
            .filter(t -> t.matches("\\d+") || t.length() >= 4).filter(t -> !STOP.contains(t))
            .map(t -> t.matches("\\d+") ? t : t.length() >= 7 ? t.substring(0, 5)
                : t.length() > 4 && t.endsWith("s") ? t.substring(0, t.length() - 1) : t)
            .collect(Collectors.toSet());
    }
    public static java.util.Map<String, Object> diagnostics(String question, String content) {
        var requested = new java.util.TreeSet<>(terms(question));
        var matched = new java.util.TreeSet<>(requested); matched.retainAll(terms(content));
        var missing = new java.util.TreeSet<>(requested); missing.removeAll(matched);
        return java.util.Map.of("matchedTerms", matched, "missingTerms", missing, "coverage", coverage(question, content));
    }
    public static double coverage(String question, String content) {
        var requested = terms(question);
        var available = terms(content);
        return requested.isEmpty() ? 0 : (double) requested.stream().filter(available::contains).count() / requested.size();
    }
}
