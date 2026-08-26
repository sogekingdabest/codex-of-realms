# Roadmap

This roadmap is outcome-oriented. A milestone is complete only when its acceptance evidence exists in the repository and can be reproduced by another developer.

## M0 — Product foundation

**Status:** Complete

- Define the product promise, primary users, and differentiator.
- Freeze the first MVP boundary and explicit exclusions.
- Establish the ubiquitous language and domain invariants.
- Record the modular-monolith, identity, and model-runtime decisions.
- Produce the initial threat model and security test strategy.
- Create an original Spanish demonstration realm.
- Create the first answerable, restricted, and unanswerable RAG cases.

**Evidence:** `docs/`, `demo/`, `README.md`, and this roadmap.

## M1 — Walking skeleton

**Status:** Implementation complete; acceptance pending a responsive local Docker engine

**Goal:** A clean clone can build, test, and start a minimal service locally.

- Create the Java 21 Spring Boot project and Maven wrapper.
- Select compatible stable Spring Boot, Spring AI, and Spring Modulith versions.
- Add PostgreSQL/pgvector, Keycloak, and the application to Docker Compose.
- Introduce Flyway, Actuator, OpenAPI, structured configuration, and health checks.
- Add the first architecture verification test.
- Add GitHub Actions for build and test.
- Document local startup, test execution, and common failures.

**Exit criteria:** `./mvnw verify` succeeds, Docker Compose becomes healthy, and no secret is stored in the repository.

**Current evidence:** `backend/`, `compose.yaml`, `infra/keycloak/`, `.github/workflows/ci.yml`, ADR-004, and the local development guide. The Java package build, module verification test, Compose validation, and secret guardrails pass locally. Full Testcontainers and service-health acceptance remains pending because the installed Docker Desktop engine did not become responsive during this run.

## M2 — Realms and authorization

**Goal:** Establish the security boundary before any lore can be retrieved.

- Implement authenticated users from OIDC claims.
- Create realms, memberships, and realm-scoped roles.
- Model `PUBLIC`, `GM_ONLY`, and member-targeted `SPOILER` access policies.
- Enforce object-level authorization in application and persistence boundaries.
- Add negative integration tests for cross-realm access and IDOR.

**Exit criteria:** unauthorized requests cannot enumerate, read, or infer the existence of protected realm content.

## M3 — Source ingestion

**Goal:** Turn Markdown and TXT sources into traceable, replaceable chunks.

- Add safe file upload with explicit size and type limits.
- Persist source documents, immutable versions, checksums, and processing status.
- Store raw files in a local volume behind a storage port.
- Split sources along structural boundaries before token-based splitting.
- Generate embeddings and persist model provenance.
- Make replacement, deletion, and reprocessing idempotent.

**Exit criteria:** every chunk can be traced to an immutable source location and removed without leaving active derived data.

## M4 — Access-aware retrieval

**Goal:** Return relevant evidence without ever retrieving unauthorized chunks.

- Introduce the `LoreRetriever` application port.
- Implement exact pgvector similarity search combined with relational authorization.
- Record rank, distance, source, and effective access policy for each result.
- Evaluate retrieval against the versioned baseline dataset.
- Add metrics for retrieval latency, result count, and score distribution.

**Exit criteria:** access precision is 100% in the security suite and answerable-query recall at `k` meets the calibrated baseline target.

## M5 — Grounded answers

**Goal:** Produce cited answers or a deterministic insufficient-evidence outcome.

- Integrate the selected local chat model through Spring AI.
- Separate user instructions from retrieved, untrusted source content.
- Add an evidence gate before generation.
- Return a structured `ANSWERED` or `INSUFFICIENT_EVIDENCE` result.
- Validate citations against the retrieved context.
- Test orchestration with deterministic model doubles in CI.

**Exit criteria:** every factual answer has valid visible citations, and restricted or unsupported questions do not leak an answer.

## M6 — Lore catalogue

**Goal:** Make important concepts explicitly navigable without automatic extraction.

- Add characters, places, factions, objects, and events.
- Add typed relations backed by optional source evidence.
- Support manual promotion of proposed content to canon.
- Expose catalogue endpoints through OpenAPI.

**Exit criteria:** entity and relation claims retain realm, access, canon, and provenance invariants.

## M7 — Portfolio hardening

**Goal:** Make the system independently understandable and demonstrable.

- Expand the Spanish demo corpus and evaluation set.
- Add refusal accuracy, citation correctness, and groundedness reports.
- Add Prometheus/Grafana only after the application exposes useful metrics.
- Run prompt-injection and authorization attack cases.
- Generate architecture diagrams and module documentation.
- Complete README, operational guide, ADR index, and demo script.

**Exit criteria:** a reviewer can run, inspect, evaluate, and discuss the system without private setup knowledge.

## Later candidates, not commitments

- PDF ingestion and OCR
- Asynchronous ingestion and a durable message broker
- Hybrid retrieval and reranking
- Automatic entity extraction
- Contradiction and timeline analysis
- Session summarization
- Neo4j or another knowledge-graph projection
- Web UI
- Browser-side model execution with WebLLM or LiteRT-LM
- English demo corpus and multilingual evaluation

Each candidate requires a functional need, an ADR, and measurable acceptance criteria before entering a milestone.
