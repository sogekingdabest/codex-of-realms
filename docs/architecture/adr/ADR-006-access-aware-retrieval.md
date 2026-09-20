# ADR-006: Filter authorized candidates before exact vector ranking

- **Status:** Accepted
- **Date:** 2026-08-27

## Context

A hidden source can be the closest vector match to a player's question. Retrieving it and filtering later risks exposing it through logs, caches, timing or accidental reuse. It also uses a result slot that could have held visible evidence.

## Decision

- Define `LoreRetriever` as a provider-neutral application port owned by the `lore` module.
- Build the authorized candidate set in PostgreSQL from active realm membership, role, access policy, spoiler grant, active source/version state, and the exact embedding provider/model/dimension.
- Materialize that candidate set before applying pgvector cosine distance.
- Use exact nearest-neighbour ordering for the MVP and deterministic chunk-ID tie-breaking.
- Return rank, distance, similarity, immutable source/version/chunk identifiers, checksum, heading, offsets, and effective access classification.
- Reject outsiders before embedding their question, while retaining the authorization predicates in the persistence query as defense in depth.
- Record latency, authorized result count, and distance distributions without realm, user, question, or source labels.
- Require 100% access precision and at least 0.90 source recall@10 against the versioned Spanish baseline before M4 acceptance.

## Consequences

The adapter uses PostgreSQL-specific SQL to express all access predicates in one query. Exact search fits the initial corpus. Before adding an approximate index, measure latency and recall on a representative dataset.

Retrieval uses one compatible embedding generation at a time, including while sources are being reprocessed after a model or dimension change.

## Rejected alternatives

- Retrieve then filter in Java, because forbidden chunks would already have crossed the security boundary.
- Let the language model decide access, because models are not authorization systems.
- Add HNSW immediately, because there is no measured corpus-size or latency need and approximate ranking complicates the recall baseline.
- Encode authorization into vector metadata alone, because membership and grants are mutable relational state.
