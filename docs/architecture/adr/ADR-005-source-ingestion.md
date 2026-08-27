# ADR-005: Store immutable source versions and explicit embedding provenance

- **Status:** Accepted
- **Date:** 2026-08-27

## Context

Retrieval and citations must remain reproducible after a source is replaced. A failed re-index must not remove the last usable version, and deletion must make every derived chunk ineligible immediately. The first deployment is local and has bounded hardware.

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

The current synchronous flow is intentionally bounded to 1 MiB. Moving it to durable background work later does not require changing version semantics.

## Rejected alternatives

- Overwriting raw files and chunks in place, because it destroys citation provenance.
- Trusting MIME type or filename alone, because both are client-controlled.
- Spring AI's generic vector store as the system of record, because M4 needs relational realm and policy predicates in the retrieval query.
- Automatic model downloads, because startup would become network-dependent and unpredictable on the target laptop.
