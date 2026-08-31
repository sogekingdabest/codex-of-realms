package dev.codexofrealms.content.application.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class StructuralChunker {
    private static final Pattern HEADING = Pattern.compile("^#{1,6}[ \\t]++\\S.*$");
    private static final Pattern HEADING_PREFIX = Pattern.compile("^#{1,6}[ \\t]++");
    private final IngestionProperties properties;

    StructuralChunker(IngestionProperties properties) {
        this.properties = properties;
    }

    List<SourceChunk> split(String text) {
        List<SourceChunk> chunks = new ArrayList<>();
        for (Section section : sections(text)) splitSection(text, section, chunks);
        if (chunks.isEmpty()) {
            throw IngestionException.invalidSource("The source has no indexable text.");
        }
        return chunks;
    }

    private List<Section> sections(String text) {
        List<Section> result = new ArrayList<>();
        String heading = null;
        int sectionStart = 0;
        int cursor = 0;
        for (String line : text.split("(?<=\\n)", -1)) {
            String clean = line.strip();
            if (HEADING.matcher(clean).matches()) {
                addSection(text, result, sectionStart, cursor, heading);
                heading = HEADING_PREFIX.matcher(clean).replaceFirst("").strip();
                sectionStart = cursor;
            }
            cursor += line.length();
        }
        addSection(text, result, sectionStart, text.length(), heading);
        return result;
    }

    private static void addSection(String text, List<Section> sections, int start, int end, String heading) {
        while (start < end && Character.isWhitespace(text.charAt(start))) start++;
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
        if (end > start) sections.add(new Section(start, end, heading));
    }

    private void splitSection(String text, Section section, List<SourceChunk> chunks) {
        int start = section.start();
        while (start < section.end()) {
            int end = chunkEnd(text, section.end(), start);
            if (end > start) {
                chunks.add(new SourceChunk(chunks.size(), section.heading(), text.substring(start, end), start, end));
            }
            if (end >= section.end()) break;
            start = nextChunkStart(text, section.end(), start, end);
        }
    }

    private int chunkEnd(String text, int sectionEnd, int start) {
        int end = Math.min(start + properties.chunkMaxCharacters(), sectionEnd);
        if (end < sectionEnd) {
            int boundary = Math.max(text.lastIndexOf("\n\n", end), text.lastIndexOf(' ', end));
            if (boundary > start + properties.chunkMaxCharacters() / 2) end = boundary;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
        return end;
    }

    private int nextChunkStart(String text, int sectionEnd, int start, int end) {
        int next = Math.max(start + 1, end - properties.chunkOverlapCharacters());
        while (next < end && !Character.isWhitespace(text.charAt(next))) next++;
        while (next < sectionEnd && Character.isWhitespace(text.charAt(next))) next++;
        return next;
    }

    private record Section(int start, int end, String heading) {
    }
}
