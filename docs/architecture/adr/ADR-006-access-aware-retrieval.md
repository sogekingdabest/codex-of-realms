# ADR-006: Filter authorized candidates before exact vector ranking

- **Status:** Accepted
- **Date:** 2026-08-27

## Context

Vector relevance does not imply authorization. Fetching nearest chunks first and removing forbidden results in application code can expose content through logs, timing, result counts, caches, or accidental reuse. It also reduces useful recall because hidden results consume the requested limit.

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

The SQL adapter is intentionally relational and PostgreSQL-specific. This is preferable to a generic vector-store abstraction that cannot express the complete authorization predicate. Exact search is sufficient for the initial corpus; an approximate index requires representative scale, a measured latency problem, and a recall study.

Embedding generations never mix. A model or dimensionality change therefore produces no partial cross-generation ranking while sources are reprocessed.

## Rejected alternatives

- Retrieve then filter in Java, because forbidden chunks would already have crossed the security boundary.
- Let the language model decide access, because models are not authorization systems.
- Add HNSW immediately, because there is no measured corpus-size or latency need and approximate ranking complicates the recall baseline.
- Encode authorization into vector metadata alone, because membership and grants are mutable relational state.
