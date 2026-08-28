# MVP Scope

This document is the scope contract for the first portfolio-ready release. A feature is outside the MVP unless it appears in the included list or is required to uphold a stated invariant.

## Included capabilities

### Realm and identity

- Authenticate through OpenID Connect using a local Keycloak deployment.
- Create and read isolated realms.
- Invite and manage realm memberships with `OWNER`, `EDITOR`, and `PLAYER` roles.
- Apply named `PUBLIC`, `GM_ONLY`, and member-targeted `SPOILER` access policies.

### Source management

- Upload Markdown and plain-text files.
- Validate type, size, name, and encoding.
- Store raw content in a local Docker volume through a replaceable storage port.
- Track immutable document versions, checksums, processing state, and failures.
- Replace, reprocess, and delete sources without leaving active stale chunks.

### Retrieval and answers

- Parse and split sources while retaining headings and source locations.
- Produce embeddings through Spring AI.
- Store relational metadata and vectors in PostgreSQL with pgvector.
- Retrieve with realm and access constraints inside the database query.
- Generate an answer from visible evidence through an interchangeable chat-model adapter.
- Return citations and a structured `INSUFFICIENT_EVIDENCE` outcome.
- Open the exact authorized source context behind a citation.
- Distinguish canonical facts from proposed AI content.

### First-party web experience

- Register and authenticate through Keycloak Authorization Code with PKCE `S256`.
- Create and switch realms without exposing internal identifiers.
- Administer invitations, spoiler groups, grants, sources, and questions in Spanish.
- Show only viewer-visible sources and distinguish insufficient evidence from model-runtime failure.
- Keep access and refresh tokens in the Keycloak adapter's memory rather than browser persistence.

### Lore catalogue

- Manually manage characters, places, factions, objects, and events.
- Manually manage typed relations.
- Attach source evidence and access policy to catalogue claims.

### Engineering quality

- Versioned REST API and OpenAPI documentation.
- Flyway-managed schema.
- Docker Compose local environment.
- Unit, module, persistence, authorization, and API integration tests.
- Deterministic model doubles for CI.
- Versioned RAG evaluation cases and reports.
- GitHub Actions build and test workflow.
- Health endpoints, structured logs, and useful Micrometer metrics.
- English README, roadmap, ADRs, architecture documentation, and operating instructions.

## Explicit exclusions

- Native desktop or mobile user interface
- PDF, OCR, images, audio, and video
- Social login and production identity hosting
- Password storage in the application
- Microservices
- Kafka, RabbitMQ, or another broker
- Kubernetes or OpenShift deployment
- Asynchronous document processing
- Distributed transactions
- Neo4j or a separate graph database
- GraphRAG
- Automatic entity and relation extraction
- Lore catalogue editing in the web interface
- Reranking
- Hybrid keyword/vector retrieval
- Contradiction detection
- Automatic timelines
- Session summaries
- Long-term conversational memory
- Autonomous agents and model tool calling
- Automatic promotion of model output to canon
- Cloud deployment
- English or multilingual demo content
- Browser-side inference through WebLLM or LiteRT-LM

## Security invariants

- Every protected record is scoped to one realm.
- Realm membership and effective access are server-derived.
- Unauthorized chunks are excluded before vector ranking.
- A citation cannot reference an unauthorized or inactive document version.
- Raw source content is untrusted data, never executable instruction.
- Model output is untrusted and cannot directly invoke privileged operations.
- Prompts, retrieved content, tokens, and answers are not written to normal logs.
- Secrets are injected at runtime and never committed.

## MVP acceptance scenario

The local demonstration contains one Game Master and two players. A public source is visible to both players, a `GM_ONLY` source is visible only to the Game Master, and one `SPOILER` source is revealed to only one player.

The same question is executed for all three identities through the browser. The Game Master and revealed player receive only the evidence they are permitted to see and can open their citations. The unrevealed player receives an evidence-insufficient response without learning whether hidden evidence exists.

This scenario must be covered by automated integration tests, the versioned RAG evaluation set, and the reproducible M8.2 acceptance command.

## Change control

Adding an excluded capability requires:

1. A concrete user or engineering need.
2. A description of why the current design cannot meet it.
3. An ADR when the change affects architecture or security.
4. Acceptance criteria and a place in the roadmap.
5. Removal or deferral of equivalent scope if the MVP would otherwise expand.
