# Architecture Decision Records

| ADR | Status | Decision |
|---|---|---|
| [ADR-001](ADR-001-modular-monolith.md) | Accepted | Start as a package-modular monolith. |
| [ADR-002](ADR-002-local-identity-provider.md) | Accepted | Use Keycloak as the local OpenID Connect provider. |
| [ADR-003](ADR-003-local-model-runtime.md) | Accepted | Use server-side local models for the MVP. |
| [ADR-004](ADR-004-m1-technology-baseline.md) | Accepted | Fix the compatible, reproducible M1 technology baseline. |
| [ADR-005](ADR-005-source-ingestion.md) | Accepted | Store immutable source versions and explicit embedding provenance. |
| [ADR-006](ADR-006-access-aware-retrieval.md) | Accepted | Filter authorized candidates before exact vector ranking. |
| [ADR-007](ADR-007-deterministic-grounded-answers.md) | Accepted | Gate and validate every generated answer outside the model. |
| [ADR-008](ADR-008-evidence-based-local-model-selection.md) | Accepted | Select the local chat model through a reproducible safety-first evaluation. |
| [ADR-009](ADR-009-manual-lore-catalogue.md) | Accepted | Keep structured lore manual, access-aware, and explicitly promoted. |
| [ADR-010](ADR-010-optional-local-observability.md) | Accepted | Keep Prometheus and Grafana as an optional local overlay. |

New ADRs use the next sequential identifier and record context, decision, consequences, and rejected alternatives. Accepted ADRs are not rewritten to hide earlier reasoning; superseding decisions link to the record they replace.
