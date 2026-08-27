package dev.codexofrealms.content.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class StructuralChunker {

    private final IngestionProperties properties;

    StructuralChunker(IngestionProperties properties) {
        this.properties = properties;
    }

    List<SourceChunk> split(String text) {
        List<Section> sections = sections(text);
        List<SourceChunk> chunks = new ArrayList<>();
        for (Section section : sections) {
            splitSection(text, section, chunks);
        }
        if (chunks.isEmpty()) {
            throw new InvalidSourceException("The source has no indexable text.");
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
            if (clean.matches("#{1,6}\\s+.+")) {
                addSection(text, result, sectionStart, cursor, heading);
                heading = clean.replaceFirst("^#{1,6}\\s+", "").strip();
                sectionStart = cursor;
            }
            cursor += line.length();
        }
        addSection(text, result, sectionStart, text.length(), heading);
        return result;
    }

    private static void addSection(
        String text,
        List<Section> sections,
        int start,
        int end,
        String heading
    ) {
        while (start < end && Character.isWhitespace(text.charAt(start))) start++;
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
        if (end > start) sections.add(new Section(start, end, heading));
    }

    private void splitSection(String text, Section section, List<SourceChunk> chunks) {
        int start = section.start();
        while (start < section.end()) {
            int end = Math.min(start + properties.chunkMaxCharacters(), section.end());
            if (end < section.end()) {
                int boundary = Math.max(text.lastIndexOf("\n\n", end), text.lastIndexOf(' ', end));
                if (boundary > start + properties.chunkMaxCharacters() / 2) end = boundary;
            }
            while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
            if (end > start) {
                chunks.add(new SourceChunk(
                    chunks.size(), section.heading(), text.substring(start, end), start, end
                ));
            }
            if (end >= section.end()) break;
            int next = Math.max(start + 1, end - properties.chunkOverlapCharacters());
            while (next < end && !Character.isWhitespace(text.charAt(next))) next++;
            while (next < section.end() && Character.isWhitespace(text.charAt(next))) next++;
            start = next;
        }
    }

    private record Section(int start, int end, String heading) {
    }
}
