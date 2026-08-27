package dev.codexofrealms.content.application;

import dev.codexofrealms.content.infrastructure.SourceVersionRecord;

record PreparedVersion(SourceVersionRecord version, SourceDocumentView existing) {

    static PreparedVersion created(SourceVersionRecord version) {
        return new PreparedVersion(version, null);
    }

    static PreparedVersion unchanged(SourceDocumentView existing) {
        return new PreparedVersion(null, existing);
    }

    boolean isUnchanged() {
        return existing != null;
    }
}
