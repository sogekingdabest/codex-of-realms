package dev.codexofrealms.content.application.ingestion;

import dev.codexofrealms.content.application.port.SourceVersion;
import dev.codexofrealms.content.application.source.SourceDocumentView;

record PreparedVersion(SourceVersion version, SourceDocumentView existing) {
    static PreparedVersion created(SourceVersion version) {
        return new PreparedVersion(version, null);
    }

    static PreparedVersion unchanged(SourceDocumentView existing) {
        return new PreparedVersion(null, existing);
    }

    boolean isUnchanged() {
        return existing != null;
    }
}
