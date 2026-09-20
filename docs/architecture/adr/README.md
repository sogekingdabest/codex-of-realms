# Architecture decision records

Each ADR records a decision at the time it was made, including the versions and evaluation results used then. For the current design, read the [decision summary](../DECISIONS.md) and [architecture](../ARCHITECTURE.md).

ADR-007 is partly superseded: answers now contain literal excerpts assembled by the server.

- [ADR-001: Start as a package-modular monolith](ADR-001-modular-monolith.md)
- [ADR-002: Use Keycloak as the local OpenID Connect provider](ADR-002-local-identity-provider.md)
- [ADR-003: Use server-side local models for the MVP](ADR-003-local-model-runtime.md)
- [ADR-004: Fix the M1 technology baseline](ADR-004-m1-technology-baseline.md)
- [ADR-005: Store immutable source versions and explicit embedding provenance](ADR-005-source-ingestion.md)
- [ADR-006: Filter authorized candidates before exact vector ranking](ADR-006-access-aware-retrieval.md)
- [ADR-007: Gate and validate every generated answer outside the model](ADR-007-deterministic-grounded-answers.md)
- [ADR-008: Select the local chat model through a reproducible safety-first evaluation](ADR-008-evidence-based-local-model-selection.md)
- [ADR-009: Keep structured lore manual, access-aware, and explicitly promoted](ADR-009-manual-lore-catalogue.md)
- [ADR-010: Keep Prometheus and Grafana as an optional local overlay](ADR-010-optional-local-observability.md)
- [ADR-011: First-party web client in the monorepo](ADR-011-first-party-web-client.md)
- [ADR-012: Close the multi-user product flow inside the modular monolith](ADR-012-multi-user-product-closure.md)
- [ADR-013: Use a deterministic local release gate with isolated restore verification](ADR-013-reproducible-local-release-gate.md)
- [ADR-014: Expose manual canon curation in the first-party web client](ADR-014-first-party-canon-workspace.md)
