package dev.codexofrealms.content.application;

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
            || other instanceof AcceptedSource that
            && Arrays.equals(bytes, that.bytes)
            && Objects.equals(text, that.text)
            && Objects.equals(originalFilename, that.originalFilename)
            && Objects.equals(mediaType, that.mediaType)
            && Objects.equals(checksum, that.checksum);
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
