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

**Status:** Complete

**Goal:** A clean clone can build, test, and start a minimal service locally.

- Create the Java 21 Spring Boot project and Maven wrapper.
- Select compatible stable Spring Boot, Spring AI, and Spring Modulith versions.
- Add PostgreSQL/pgvector, Keycloak, and the application to Docker Compose.
- Introduce Flyway, Actuator, OpenAPI, structured configuration, and health checks.
- Add the first architecture verification test.
- Add GitHub Actions for build and test.
- Document local startup, test execution, and common failures.

**Exit criteria:** `./mvnw verify` succeeds, Docker Compose becomes healthy, and no secret is stored in the repository.

**Current evidence:** `backend/`, `compose.yaml`, `infra/keycloak/`, `.github/workflows/ci.yml`, ADR-004, and the local development guide. The Java package build, module verification, Compose validation, secret guardrails, PostgreSQL/pgvector Testcontainers suite, and full local service-health acceptance pass. M8 subsequently extends the healthy topology with the web service.

## M2 — Realms and authorization

**Status:** Implementation complete; authenticated model-backed smoke pending

**Goal:** Establish the security boundary before any lore can be retrieved.

- Implement authenticated users from OIDC claims.
- Create realms, memberships, and realm-scoped roles.
- Model `PUBLIC`, `GM_ONLY`, and member-targeted `SPOILER` access policies.
- Enforce object-level authorization in application and persistence boundaries.
- Add negative integration tests for cross-realm access and IDOR.

**Exit criteria:** unauthorized requests cannot enumerate, read, or infer the existence of protected realm content.

**Current evidence:** OIDC identity synchronization, Flyway authorization schema, realm and membership application services, SQL-level effective-access predicates, non-disclosing API errors, domain matrix tests, and seven passing PostgreSQL-backed acceptance cases in `RealmAuthorizationIntegrationTest`.

## M3 — Source ingestion

**Status:** Complete

**Goal:** Turn Markdown and TXT sources into traceable, replaceable chunks.

- Add safe file upload with explicit size and type limits.
- Persist source documents, immutable versions, checksums, and processing status.
- Store raw files in a local volume behind a storage port.
- Split sources along structural boundaries before token-based splitting.
- Generate embeddings and persist model provenance.
- Make replacement, deletion, and reprocessing idempotent.

**Exit criteria:** every chunk can be traced to an immutable source location and removed without leaving active derived data.

**Current evidence:** Flyway source/version/chunk schema, realm-facing authorization facade, bounded UTF-8 upload validation, local storage port, structural chunker with exact offsets, Spring AI embedding adapter, model/pipeline provenance, idempotent management endpoints, deterministic unit tests, and the passing PostgreSQL-backed source lifecycle case.

## M4 — Access-aware retrieval

**Status:** Complete

**Goal:** Return relevant evidence without ever retrieving unauthorized chunks.

- Introduce the `LoreRetriever` application port.
- Implement exact pgvector similarity search combined with relational authorization.
- Record rank, distance, source, and effective access policy for each result.
- Evaluate retrieval against the versioned baseline dataset.
- Add metrics for retrieval latency, result count, and score distribution.

**Exit criteria:** access precision is 100% in the security suite and answerable-query recall at `k` meets the calibrated baseline target.

**Current evidence:** public `LoreRetriever` port, exact pgvector cosine query with authorization inside its materialized candidate set, active embedding-generation checks, ranked provenance-rich results, low-cardinality Micrometer metrics, and a passing PostgreSQL integration evaluation against `demo/evaluation/baseline.json`. The suite enforces access precision of 100% and recall@10 of at least 0.90.

## M5 — Grounded answers

**Status:** Complete

**Goal:** Produce cited answers or a deterministic insufficient-evidence outcome.

- Integrate the selected local chat model through Spring AI.
- Separate user instructions from retrieved, untrusted source content.
- Add an evidence gate before generation.
- Return a structured `ANSWERED` or `INSUFFICIENT_EVIDENCE` result.
- Validate citations against the retrieved context.
- Test orchestration with deterministic model doubles in CI.

**Exit criteria:** every factual answer has valid visible citations, and restricted or unsupported questions do not leak an answer.

**Current evidence:** public `LoreSearch` boundary, deterministic evidence gate, Spring AI `ChatModel` adapter, untrusted-evidence prompt separation, structured claim output, fail-closed citation and groundedness validation, low-cardinality metrics, unit orchestration with deterministic model doubles, and passing PostgreSQL/API classification of every baseline case. A real `qwen3.5:4b` smoke also produced only valid structured outcomes and citations with zero security failures; comparative model selection remains isolated in M5.1.

### M5.1 — Local model selection and runtime calibration

**Status:** Implementation complete; one-run target-hardware smoke passed, reviewed comparison pending

- Make the chat model selectable without rebuilding the application.
- Add an explicit NVIDIA GPU Compose override while retaining a CPU-compatible base stack.
- Request provider-native JSON Schema and retain deterministic output validation.
- Compare the incumbent with current compact candidates against the versioned Spanish baseline.
- Record quality, citations, security failures, latency, throughput, runtime state, hardware, and Git commit.
- Keep real-model execution opt-in so normal CI remains deterministic and download-free.

**Exit criteria:** at least one reviewed three-run comparison exists on the target RTX 3060 Mobile; the selected model has zero security failures, meets every quality threshold, fits the available memory, and its exact tag is recorded.

**Current evidence:** ADR-008, GPU Compose override, provider-native JSON Schema, test-only Ollama telemetry adapter, opt-in Maven profile, comparison script, Spanish generation dataset harness, deterministic adapter tests, and the documented `qwen3.5:4b` smoke at commit `709495c`. That single run met every eligibility threshold with zero security failures and full GPU residency. It is calibration evidence, not the required reviewed three-run comparison.

## M6 — Lore catalogue

**Status:** Complete

**Goal:** Make important concepts explicitly navigable without automatic extraction.

- Add characters, places, factions, objects, and events.
- Add typed relations backed by optional source evidence.
- Support manual promotion of proposed content to canon.
- Expose catalogue endpoints through OpenAPI.

**Exit criteria:** entity and relation claims retain realm, access, canon, and provenance invariants.

**Current evidence:** Flyway V5, the `LoreCatalogueService`, SQL-level entity/relation visibility predicates,
immutable source-evidence snapshots, append-only promotion history, the OpenAPI catalogue controller, pure domain
tests, ADR-009, and the passing PostgreSQL-backed catalogue acceptance case. The complete suite passes 40 tests.

## M7 — Portfolio hardening

**Status:** Complete

**Goal:** Make the system independently understandable and demonstrable.

- Expand the Spanish demo corpus and evaluation set.
- Add refusal accuracy, citation correctness, and groundedness reports.
- Add Prometheus/Grafana only after the application exposes useful metrics.
- Run prompt-injection and authorization attack cases.
- Generate architecture diagrams and module documentation.
- Complete README, operational guide, ADR index, and demo script.

**Exit criteria:** a reviewer can run, inspect, evaluate, and discuss the system without private setup knowledge.

**Current evidence:** baseline v2 contains 22 Spanish cases over seven original sources; deterministic acceptance writes refusal, citation, groundedness, retrieval, and attack metrics; direct and indirect prompt injection fail closed; the existing authorization matrix remains PostgreSQL-backed; Spring Modulith generates module diagrams and canvases; and the optional observability overlay provisions pinned Prometheus/Grafana services and a content-safe dashboard. `scripts/demo.ps1`, the reviewer runbook, ADR-010, and the consolidated M7 report complete the handoff. The full suite passes 42 tests.

## M8 — First web interface

**Status:** Implementation complete; superseded by M8.1 product closure

**Goal:** Expose the secure evidence-first path through a usable Spanish browser interface.

- Add a React and TypeScript application under `frontend/` without splitting the product repository.
- Authenticate with Keycloak Authorization Code flow and PKCE `S256`, retaining tokens only in memory.
- Support first-realm creation and switching between authorized realms.
- Let owners and editors create an access policy, upload Markdown or TXT sources, and inspect processing state.
- Let every member ask questions and inspect exact citations, refusal state, and model provenance.
- Serve the built SPA through a same-origin API proxy in Compose and verify it independently in CI.

**Exit criteria:** a configured demo user can log in, select or create a realm, upload an authorized source, ask a grounded question, and inspect its citations without using Swagger; frontend lint, unit tests, production build, backend verification, and Compose validation pass.

**Current evidence:** ADR-011, exact Keycloak web origins, the in-memory `keycloak-js` session adapter, typed API client, access-aware policy listing, responsive Spanish interface, Nginx/Vite API proxies, four frontend tests, and CI/Compose integration. All five containers reached healthy state, and a browser smoke confirmed an Authorization Code request with an `S256` challenge and the exact port-5173 redirect. Completing the interactive upload/question path remains intentionally pending a local demo-user password and the parked Ollama models.

## M8.1 — Multi-user product closure

**Status:** Implementation complete; model-backed multi-account smoke pending

**Goal:** Make the owner, editor, and player journey operable without Swagger, internal UUIDs, or disposable identity state.

- Create named base policies with each realm and named spoiler groups on demand.
- Invite members by email, accept pending invitations on first OIDC login, and administer memberships and spoiler grants.
- Let players browse only visible sources and let every citation open its exact authorized source context.
- Expose installed-model capabilities and distinguish runtime failure from insufficient evidence.
- Persist Keycloak in PostgreSQL and provide coordinated backup and restore scripts.
- Cover invitation acceptance, player source visibility, frontend player behavior, lint, and production build.

**Exit criteria:** an owner can register, create a realm, invite two users, reveal a named spoiler to one player, upload
a source, obtain role-dependent answers, and open citations entirely through the browser; identity and content survive
container recreation and the complete deterministic suites pass.

**Current evidence:** Flyway V6, ADR-012, email invitation acceptance during identity synchronization, named policies,
member/grant APIs, access-aware source browsing, authorized source-version content, safe answer failure reasons,
runtime capabilities, persistent Keycloak schema configuration, a successful coordinated backup, and the expanded
Spanish web UI. Backend verification passes 43 tests; frontend lint, all 6 tests, and the production build pass. A live
browser smoke verified Spanish registration and Authorization Code + PKCE `S256`, then a Keycloak container recreation
proved the realm remained available. The final multi-account answer comparison remains parked with local model testing.

## Later candidates, not commitments

- PDF ingestion and OCR
- Asynchronous ingestion and a durable message broker
- Hybrid retrieval and reranking
- Automatic entity extraction
- Contradiction and timeline analysis
- Session summarization
- Neo4j or another knowledge-graph projection
- Lore catalogue UI
- Browser-side model execution with WebLLM or LiteRT-LM
- English demo corpus and multilingual evaluation

Each candidate requires a functional need, an ADR, and measurable acceptance criteria before entering a milestone.
