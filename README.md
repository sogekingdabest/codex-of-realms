# Codex of Realms

Codex of Realms is an evidence-first lore assistant for fictional worlds and tabletop role-playing campaigns. It turns curated source documents into an access-aware knowledge base that can answer natural-language questions, cite the exact evidence it used, and decline to answer when the available evidence is insufficient.

The product is deliberately narrower than a general-purpose worldbuilding suite. Its core promise is trustworthy canon retrieval: every factual answer is grounded in visible sources, spoiler rules are enforced before retrieval, and AI-generated suggestions never become canon without an explicit human decision.

## Project status

**M9 — Canon workspace** is complete. The Spanish web application now joins the evidence-first archive with an
access-aware **Atlas del canon**. Owners and editors can create characters, places, factions, objects, events, and
directional relations; attach exact source fragments; edit proposals; and explicitly promote them to canon. Players
receive the same navigable catalogue filtered by their effective access, without mutation controls.

The earlier M8.2 release gate remains the reproducible local-product baseline: Keycloak identity persists in
PostgreSQL, coordinated backup/restore covers the database and raw sources, and live-model comparison stays parked
rather than blocking catalogue development.

The deterministic backend suite includes PostgreSQL/pgvector acceptance of invitation activation, role and grant
boundaries, player-visible source browsing, ingestion, retrieval, grounded answers, and catalogue invariants. The
frontend adds owner/editor/player component and API-client tests plus lint and production-build checks. Baseline v2 covers 22
cases over seven original Spanish sources; real-model promotion still requires the reviewed M5.1 comparison.

The M0 product foundation remains the source of truth for scope, domain language, security invariants, original Spanish demonstration lore, and the RAG evaluation baseline.

## Product principles

- Evidence before eloquence.
- Authorization before retrieval.
- Canon is controlled by people, not by the model.
- Start as a modular monolith and split only when measured needs justify it.
- Prefer a small, demonstrable vertical slice over a broad feature catalogue.
- Treat documents, prompts, and model output as untrusted input.

## M1 technology baseline

- Eclipse Temurin JDK 21 and Spring Boot 4.1.1
- Spring AI 2.0.1 and Spring Security
- Spring Modulith 2.1.1
- PostgreSQL 18 with pgvector 0.8.6
- Keycloak 26.7.2 as the local OpenID Connect provider
- Ollama 0.32.5 as the default local model runtime
- Flyway, Actuator, OpenAPI, Docker Compose, Testcontainers, and GitHub Actions

M8 adds React 19.2, TypeScript 6, Vite 8, Node.js 24 LTS, the official Keycloak JavaScript adapter, and an unprivileged Nginx runtime container.

M3 selects `bge-m3` as the first Spanish-capable embedding baseline. `qwen3:4b` remains the incumbent chat model for the 6 GB GPU target until M5.1 produces reviewed evidence for a replacement. The application and evaluation script never download models implicitly.

## M0 documentation

- [Product charter](docs/product/PRODUCT_CHARTER.md)
- [MVP scope](docs/product/MVP_SCOPE.md)
- [Initial domain model](docs/product/DOMAIN_MODEL.md)
- [Ubiquitous language](docs/product/GLOSSARY.md)
- [Initial architecture](docs/architecture/ARCHITECTURE.md)
- [Architecture decision records](docs/architecture/adr/)
- [Threat model](docs/security/THREAT_MODEL.md)
- [Realm authorization model](docs/security/AUTHORIZATION_MODEL.md)
- [Source ingestion runbook](docs/operations/SOURCE_INGESTION.md)
- [Retrieval baseline](docs/evaluation/RETRIEVAL_BASELINE.md)
- [Local chat-model evaluation](docs/evaluation/LOCAL_MODEL_EVALUATION.md)
- [Grounded-answer runbook](docs/operations/GROUNDED_ANSWERS.md)
- [Lore catalogue runbook](docs/operations/LORE_CATALOGUE.md)
- [Canon workspace](docs/operations/CANON_WORKSPACE.md)
- [M7 deterministic quality report](docs/evaluation/PORTFOLIO_REPORT.md)
- [Reviewer demo](docs/operations/DEMO.md)
- [Local observability](docs/operations/OBSERVABILITY.md)
- [Web UI](docs/operations/WEB_UI.md)
- [Backup and restore](docs/operations/BACKUP_AND_RESTORE.md)
- [M8.2 local product acceptance](docs/evaluation/M8_2_ACCEPTANCE.md)
- [Application modules](docs/architecture/MODULES.md)
- [Roadmap](ROADMAP.md)
- [Original Spanish demo realm](demo/README.md)
- [Baseline RAG evaluation set](demo/evaluation/README.md)

## Repository shape

```text
backend/                 Java 21 Spring Boot application
frontend/                React and TypeScript web application
demo/lore/               Original Spanish canonical source documents
demo/evaluation/         Versioned RAG evaluation cases
docs/product/            Product definition and scope
docs/architecture/adr/   Architecture decision records
docs/security/           Threat model and security requirements
docs/operations/         Local runbooks and troubleshooting
infra/keycloak/          Importable local OIDC realm
ops/                     Local observability assets (introduced when needed)
```

## Current constraints

The initial development target has 16 GB of system RAM, an NVIDIA RTX 3060 Mobile with 6 GB of VRAM, and an AMD Ryzen 7 5800H. The MVP therefore targets small, quantized local chat models and a multilingual embedding model. Browser-side inference through WebLLM or LiteRT-LM is a possible later capability, not an MVP dependency.

## Running and testing

Copy **.env.example** to **.env**, set both local passwords, then start the stack:

~~~powershell
docker compose up --build
~~~

Then open <http://localhost:5173>. See the [web UI guide](docs/operations/WEB_UI.md) for Keycloak demo-user setup and the first-use flow.

Run the complete test suite from **backend**:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress verify
~~~

Verify the web application from **frontend**:

~~~powershell
npm ci
npm run lint
npm run test
npm run build
~~~

Docker must be running because the integration suite starts PostgreSQL/pgvector through Testcontainers. See the [local development guide](docs/operations/LOCAL_DEVELOPMENT.md) for endpoints, non-Docker checks, and troubleshooting.

Run the reviewer workflow from the repository root:

~~~powershell
.\scripts\demo.ps1
~~~

Use `-StartStack` after configuring `.env`, and add `-WithObservability` for the optional Prometheus/Grafana overlay. See the [reviewer demo](docs/operations/DEMO.md) for the complete walkthrough.

With the normal stack running, execute the complete deterministic product and recovery gate:

~~~powershell
.\scripts\verify-m8.2.ps1
~~~

This does not require installed model weights. Add `-WithLiveModel` only when deliberately running the separate M5.1
quality benchmark.

Once Ollama and the candidate weights are prepared, run the opt-in Spanish model comparison from the repository root:

~~~powershell
.\scripts\evaluate-local-models.ps1 -Repetitions 3
~~~

See the [evaluation guide](docs/evaluation/LOCAL_MODEL_EVALUATION.md) before interpreting or promoting a result.

## Intellectual property

All demonstration lore in this repository is original project material. It must not contain names, text, maps, logos, or other protected assets from existing fantasy franchises.
