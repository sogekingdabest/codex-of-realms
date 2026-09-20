package dev.codexofrealms.content.application.evidence;

import dev.codexofrealms.content.SourcePassage;
import dev.codexofrealms.content.application.port.RawSourceStorage;
import dev.codexofrealms.content.application.port.SourceRepository;
import dev.codexofrealms.content.application.source.SourceException;
import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class VisiblePassageService {
    @org.springframework.beans.factory.annotation.Value("${codex.retrieval.contextual-passages-enabled:false}")
    private boolean contextualPassagesEnabled;
    private final SourceRepository repository;
    private final RawSourceStorage storage;

    public VisiblePassageService(SourceRepository repository, RawSourceStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    public List<SourcePassage> paragraphs(UUID realmId, UUID userId, UUID documentId, UUID versionId,
                                         int start, int end) {
        String text = visibleText(realmId, userId, documentId, versionId);
        return (contextualPassagesEnabled ? contextualSplit(text) : split(text)).stream().filter(p -> p.startOffset() < end && p.endOffset() > start).toList();
    }

    public boolean matches(UUID realmId, UUID userId, UUID documentId, UUID versionId, SourcePassage passage) {
        return matchesAll(realmId, userId, documentId, versionId, List.of(passage));
    }

    public boolean matchesAll(UUID realmId, UUID userId, UUID documentId, UUID versionId, List<SourcePassage> passages) {
        try {
            String text = visibleText(realmId, userId, documentId, versionId);
            return passages.stream().allMatch(passage -> passage.startOffset() >= 0 && passage.endOffset() <= text.length()
                && passage.endOffset() > passage.startOffset()
                && text.substring(passage.startOffset(), passage.endOffset()).equals(passage.content()));
        } catch (SourceException | IllegalStateException exception) {
            return false;
        }
    }

    private String visibleText(UUID realmId, UUID userId, UUID documentId, UUID versionId) {
        var version = repository.findAccessibleVersion(realmId, documentId, versionId, userId)
            .orElseThrow(SourceException::unavailable);
        return new String(storage.read(version.storageKey()), StandardCharsets.UTF_8);
    }

    public static List<SourcePassage> split(String text) {
        return analyze(text).passages();
    }

    /** Merge only across original whitespace: never across headings or excluded text. */
    public static List<SourcePassage> contextualSplit(String text) {
        var result = new ArrayList<SourcePassage>();
        for (var passage : split(text)) {
            if (!result.isEmpty()) {
                var previous = result.getLast();
                if (previous.endOffset() <= passage.startOffset()
                    && passage.endOffset() - previous.startOffset() <= 2000
                    && text.substring(previous.endOffset(), passage.startOffset()).isBlank()) {
                    result.set(result.size() - 1, new SourcePassage(
                        text.substring(previous.startOffset(), passage.endOffset()), previous.startOffset(), passage.endOffset()));
                    continue;
                }
            }
            result.add(passage);
        }
        return List.copyOf(result);
    }

    public record Analysis(List<SourcePassage> passages, int excludedSentences) {}

    public static Analysis analyze(String text) {
        List<SourcePassage> result = new ArrayList<>();
        int excluded = 0;
        int cursor = 0;
        int paragraphStart = -1;
        for (String line : text.split("(?<=\\n)", -1)) {
            boolean boundary = line.isBlank() || line.strip().matches("#{1,6}[ \\t]+.*");
            if (boundary) {
                excluded += add(result, text, paragraphStart, cursor);
                paragraphStart = -1;
            } else if (paragraphStart < 0) {
                paragraphStart = cursor;
            }
            cursor += line.length();
        }
        excluded += add(result, text, paragraphStart, cursor);
        return new Analysis(List.copyOf(result), excluded);
    }

    private static int add(List<SourcePassage> result, String text, int start, int end) {
        if (start < 0) return 0;
        while (start < end && Character.isWhitespace(text.charAt(start))) start++;
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
        if (end <= start) return 0;
        if (end - start <= 2000) {
            result.add(new SourcePassage(text.substring(start, end), start, end));
            return 0;
        }
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.forLanguageTag("es"));
        iterator.setText(text.substring(start, end));
        var sentences = new ArrayList<SourcePassage>();
        int left = iterator.first();
        for (int right = iterator.next(); right != BreakIterator.DONE; left = right, right = iterator.next()) {
            int a = start + left, b = start + right;
            while (a < b && Character.isWhitespace(text.charAt(a))) a++;
            while (b > a && Character.isWhitespace(text.charAt(b - 1))) b--;
            if (a < b) sentences.add(new SourcePassage(text.substring(a, b), a, b));
        }
        int excluded = 0;
        for (int i = 0; i < sentences.size();) {
            var first = sentences.get(i);
            if (first.content().length() > 2000) { excluded++; i++; continue; }
            int last = i;
            while (last + 1 < sentences.size()
                && sentences.get(last + 1).endOffset() - first.startOffset() <= 2000) last++;
            int a = first.startOffset(), b = sentences.get(last).endOffset();
            result.add(new SourcePassage(text.substring(a, b), a, b));
            // Overlap only if the previous final sentence and a NEW sentence fit.
            i = last > i && last + 1 < sentences.size()
                && sentences.get(last + 1).endOffset() - sentences.get(last).startOffset() <= 2000
                ? last : last + 1;
        }
        return excluded;
    }
}
