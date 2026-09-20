package dev.codexofrealms.qa.application.answering;

import dev.codexofrealms.lore.PassageRelevance;
import dev.codexofrealms.lore.RetrievedEvidence;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative omission checks, not a semantic correctness evaluator. */
public final class SelectionCompleteness {
    private SelectionCompleteness() {}
    public static List<String> parts(String question) {
        return Arrays.stream(question.split("(?iuU)\\s+(?:y|e)\\s+(?=¿?\\s*(?:qué|cuál|cuáles|cuánto|cuánta|cuántos|cuántas|cuándo|dónde|quién|quiénes|cómo|por qué)\\b)"))
            .map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
    private static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
    }
    private static final Pattern QUANTITY = Pattern.compile("\\bcuant(?:o|a|os|as)\\b");
    private static final Pattern NUMBER = Pattern.compile("\\b(?:[0-9]+|cero|un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|dieci[a-z]+|veinti[a-z]+|veinte|treinta|cuarenta|cincuenta|sesenta|setenta|ochenta|noventa|cien|ciento|doscientos|trescientos|mil|millon|millones)\\b");
    public static List<String> missing(String question, List<RetrievedEvidence> offered, List<RetrievedEvidence> selected) {
        String available = join(offered), answer = join(selected);
        var missing = new ArrayList<String>();
        for (String part : parts(question)) {
            double required = Math.min(.70, PassageRelevance.coverage(part, available));
            boolean omitted = PassageRelevance.coverage(part, answer) + 1e-9 < required;
            if (QUANTITY.matcher(normalize(part)).find()) {
                // Require number and explicit short names in the SAME selected passage.
                var names = Pattern.compile("(?U)\\b\\p{Lu}\\p{Ll}{0,2}\\b").matcher(part);
                var anchors = new ArrayList<String>();
                while (names.find()) {
                    String name = normalize(names.group());
                    if (!Set.of("que", "por", "en", "de", "el", "la", "un", "una", "y", "o", "a").contains(name)) anchors.add(name);
                }
                omitted |= selected.stream().noneMatch(p -> {
                    String text = normalize(p.content());
                    return PassageRelevance.coverage(part, p.content()) + 1e-9 >= required
                        && NUMBER.matcher(text).find() && anchors.stream().allMatch(a ->
                        Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(a) + "(?![\\p{L}\\p{N}])").matcher(text).find());
                });
            }
            if (omitted) missing.add(part);
        }
        return List.copyOf(missing);
    }
    private static String join(List<RetrievedEvidence> passages) {
        return passages.stream().map(RetrievedEvidence::content).collect(java.util.stream.Collectors.joining("\n"));
    }
}
