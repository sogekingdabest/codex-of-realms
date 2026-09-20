package dev.codexofrealms.content.application.evidence;

import dev.codexofrealms.content.SourcePassage;
import dev.codexofrealms.content.application.port.RawSourceStorage;
import dev.codexofrealms.content.application.port.SourceRepository;
import dev.codexofrealms.content.application.source.SourceException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

public class ReferenceVisiblePassageService extends VisiblePassageService {
    private final SourceRepository repository;
    private final RawSourceStorage storage;

    public ReferenceVisiblePassageService(SourceRepository repository, RawSourceStorage storage) {
        super(repository, storage);
        this.repository = repository;
        this.storage = storage;
    }

    public List<SourcePassage> paragraphs(UUID realmId, UUID userId, UUID documentId, UUID versionId,
                                         int start, int end) {
        String text = visibleText(realmId, userId, documentId, versionId);
        return split(text).stream().filter(p -> p.startOffset() < end && p.endOffset() > start).toList();
    }

    public boolean matches(UUID realmId, UUID userId, UUID documentId, UUID versionId, SourcePassage passage) {
        try {
            String text = visibleText(realmId, userId, documentId, versionId);
            return passage.startOffset() >= 0 && passage.endOffset() <= text.length()
                && passage.endOffset() > passage.startOffset()
                && text.substring(passage.startOffset(), passage.endOffset()).equals(passage.content());
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
        List<SourcePassage> result = new ArrayList<>();
        int cursor = 0;
        int paragraphStart = -1;
        for (String line : text.split("(?<=\\n)", -1)) {
            boolean boundary = line.isBlank() || line.strip().matches("#{1,6}[ \\t]+.*");
            if (boundary) {
                add(result, text, paragraphStart, cursor);
                paragraphStart = -1;
            } else if (paragraphStart < 0) {
                paragraphStart = cursor;
            }
            cursor += line.length();
        }
        add(result, text, paragraphStart, cursor);
        return List.copyOf(result);
    }

    private static void add(List<SourcePassage> result, String text, int start, int end) {
        if (start < 0) return;
        while (start < end && Character.isWhitespace(text.charAt(start))) start++;
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
        if (end > start && end - start <= 2000) result.add(new SourcePassage(text.substring(start, end), start, end));
    }
}
