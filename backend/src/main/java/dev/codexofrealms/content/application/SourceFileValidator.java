package dev.codexofrealms.content.application;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class SourceFileValidator {

    private static final Set<String> EXTENSIONS = Set.of("md", "markdown", "txt");
    private static final Set<String> MEDIA_TYPES = Set.of(
        "text/plain", "text/markdown", "application/octet-stream"
    );
    private final IngestionProperties properties;

    SourceFileValidator(IngestionProperties properties) {
        this.properties = properties;
    }

    AcceptedSource validate(byte[] bytes, String filename, String contentType) {
        if (bytes == null || bytes.length == 0 || bytes.length > properties.maxFileBytes()) {
            throw new InvalidSourceException("The source must be non-empty and within the upload limit.");
        }
        String safeName = safeFilename(filename);
        String extension = safeName.substring(safeName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension)) {
            throw new InvalidSourceException("Only Markdown and TXT sources are accepted.");
        }
        String normalizedType = contentType == null ? "application/octet-stream" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!MEDIA_TYPES.contains(normalizedType)) {
            throw new InvalidSourceException("The declared media type is not supported.");
        }
        String text = decodeUtf8(bytes);
        if (text.indexOf('\0') >= 0 || text.isBlank()) {
            throw new InvalidSourceException("The source must contain valid text without null bytes.");
        }
        if (text.chars().anyMatch(character -> Character.isISOControl(character)
            && character != '\n' && character != '\r' && character != '\t')) {
            throw new InvalidSourceException("The source contains unsupported control characters.");
        }
        return new AcceptedSource(bytes.clone(), text, safeName,
            extension.equals("txt") ? "text/plain" : "text/markdown", sha256(bytes));
    }

    private static String safeFilename(String filename) {
        String value = filename == null ? "" : filename.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        if (value.isEmpty() || value.length() > 255 || value.lastIndexOf('.') <= 0) {
            throw new InvalidSourceException("A valid source filename is required.");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidSourceException("The source filename contains control characters.");
        }
        return value;
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new InvalidSourceException("The source must be valid UTF-8.");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
