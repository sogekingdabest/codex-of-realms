package dev.codexofrealms.content.application;

record AcceptedSource(
    byte[] bytes,
    String text,
    String originalFilename,
    String mediaType,
    String checksum
) {
}
