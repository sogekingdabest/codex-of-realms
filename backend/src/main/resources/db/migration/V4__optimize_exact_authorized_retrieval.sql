CREATE INDEX idx_document_version_active_embedding_generation
    ON document_version (
        realm_id,
        embedding_provider,
        embedding_model,
        embedding_dimension,
        document_id,
        id
    )
    WHERE active AND processing_status = 'READY';

COMMENT ON INDEX idx_document_version_active_embedding_generation IS
    'Narrows exact retrieval to one active realm-scoped embedding generation before vector ranking.';
