# ADR-010: Keep Prometheus and Grafana as an optional local overlay

- **Status:** Accepted
- **Date:** 2026-08-28

## Context

Retrieval and answering now expose metrics worth inspecting. Prometheus and Grafana make them easier to read, but would use memory needed by local models if they started with every development session.

## Decision

Expose Micrometer's Prometheus scrape endpoint and provide pinned Prometheus and Grafana services in `compose.observability.yaml` behind the `observability` Compose profile. Configure one data source and one dashboard from versioned files.

Metric labels must not contain realm or user identifiers, prompts, chunks, answers, source titles, tokens, or other lore content. The local scrape endpoint is unauthenticated for service-to-service collection and must not be exposed to an untrusted network.

## Consequences

- The base four-service stack remains unchanged in size and startup behavior.
- Reviewers can opt into a reproducible dashboard with one Compose overlay.
- Operations can inspect outcomes and latency without collecting campaign content.
- Production deployment still requires network isolation or authenticated management ingress.

## Rejected alternatives

- Always start Prometheus and Grafana: unnecessary resource use on the 16 GB target machine.
- Put user, realm, or source identifiers in labels: creates sensitive telemetry and unbounded cardinality.
- Protect the local scrape with application JWTs: Prometheus would need an end-user token lifecycle and broader identity coupling; network isolation is the clearer deployment control.
