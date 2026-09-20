# ADR-005: Store immutable source versions and explicit embedding provenance

- **Status:** Accepted
- **Date:** 2026-08-27

## Context

Replacing a source must preserve its citation history. If re-indexing fails, readers still need the last usable version; if the source is deleted, its chunks must stop appearing in search immediately. The design also has to fit the local machine.

## Decision

- Keep a logical `source_document` and append-only `document_version` records.
- Permit at most one active `READY` version per document through a partial unique index.
- Store raw bytes behind an application port in a generated, realm-scoped local path; never derive paths from client filenames.
- Store SHA-256, media type, language, policy, pipeline fingerprint, embedding provider/model/dimension, headings, and exact character offsets.
- Split Markdown at headings before applying bounded overlapping windows.
- Activate a processed version and retire its predecessor in one database transaction.
- Use pgvector without a fixed schema dimension so a model migration can coexist with historical retired versions. Retrieval will select one compatible active embedding generation.
- Use Spring AI's embedding abstraction with Ollama `bge-m3` as the first local Spanish/multilingual baseline. Never auto-pull models at startup.
- Use deterministic embedding doubles in automated tests.

## Consequences

Replacement consumes additional storage until a source is deleted, but old citations and processing history remain explainable. Failed versions retain safe failure codes and cannot become active. Reprocessing is a no-op when source, policy, and pipeline fingerprint have not changed.

At this milestone, ingestion runs synchronously with a 1 MiB file limit. Persistent background processing can reuse the same version rules.

## Rejected alternatives

- Overwriting raw files and chunks in place, because it destroys citation provenance.
- Trusting MIME type or filename alone, because both are client-controlled.
- Spring AI's generic vector store as the system of record, because M4 needs relational realm and policy predicates in the retrieval query.
- Automatic model downloads, because startup would become network-dependent and unpredictable on the target laptop.
