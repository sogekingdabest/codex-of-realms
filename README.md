# Codex of Realms

Codex of Realms is an evidence-first lore assistant for fictional worlds and tabletop role-playing campaigns. It turns curated source documents into an access-aware knowledge base that can answer natural-language questions, cite the exact evidence it used, and decline to answer when the available evidence is insufficient.

The product is deliberately narrower than a general-purpose worldbuilding suite. Its core promise is trustworthy canon retrieval: every factual answer is grounded in visible sources, spoiler rules are enforced before retrieval, and AI-generated suggestions never become canon without an explicit human decision.

## Project status

**M5 — Grounded answers** is implemented in code. Realm members can receive a cited `ANSWERED` result or a fail-closed `INSUFFICIENT_EVIDENCE` result. Authorization remains inside retrieval; deterministic evidence and output gates surround the local chat model.

M1–M5 still await full local container-backed acceptance because Docker Desktop has not exposed a responsive engine in this environment. Compilation, deterministic tests, module verification, Compose validation, and integration-test compilation succeed. M5 also awaits a recorded quality run against the real local chat model.

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
- Ollama 0.32.5 as the future default local model runtime
- Flyway, Actuator, OpenAPI, Docker Compose, Testcontainers, and GitHub Actions

M3 selects `bge-m3` as the first Spanish-capable embedding baseline. M5 selects the multilingual `qwen3:4b` chat model for the 6 GB GPU target. The application never downloads either model implicitly; prepare them with `ollama pull bge-m3` and `ollama pull qwen3:4b`.

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
- [Grounded-answer runbook](docs/operations/GROUNDED_ANSWERS.md)
- [Roadmap](ROADMAP.md)
- [Original Spanish demo realm](demo/README.md)
- [Baseline RAG evaluation set](demo/evaluation/README.md)

## Repository shape

```text
backend/                 Java 21 Spring Boot application
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

Run the complete test suite from **backend**:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress verify
~~~

Docker must be running because the integration suite starts PostgreSQL/pgvector through Testcontainers. See the [local development guide](docs/operations/LOCAL_DEVELOPMENT.md) for endpoints, non-Docker checks, and troubleshooting.

## Intellectual property

All demonstration lore in this repository is original project material. It must not contain names, text, maps, logos, or other protected assets from existing fantasy franchises.
