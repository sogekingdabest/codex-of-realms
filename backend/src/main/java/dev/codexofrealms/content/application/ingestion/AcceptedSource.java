package dev.codexofrealms.content.application.ingestion;

import java.util.Arrays;
import java.util.Objects;

record AcceptedSource(
    byte[] bytes,
    String text,
    String originalFilename,
    String mediaType,
    String checksum
) {
    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof AcceptedSource(
                byte[] thatBytes,
                String thatText,
                String thatOriginalFilename,
                String thatMediaType,
                String thatChecksum
            )
            && Arrays.equals(bytes, thatBytes)
            && Objects.equals(text, thatText)
            && Objects.equals(originalFilename, thatOriginalFilename)
            && Objects.equals(mediaType, thatMediaType)
            && Objects.equals(checksum, thatChecksum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(bytes), text, originalFilename, mediaType, checksum);
    }

    @Override
    public String toString() {
        return "AcceptedSource[bytes=" + Arrays.toString(bytes)
            + ", text=" + text
            + ", originalFilename=" + originalFilename
            + ", mediaType=" + mediaType
            + ", checksum=" + checksum + "]";
    }
}
