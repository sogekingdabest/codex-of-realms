# MVP Scope

The first release covers a local campaign archive for a Game Master and their players. The lists below define what belongs in that release and what can wait.

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
- Process documents asynchronously through persistent PostgreSQL jobs with bounded automatic retries and manual recovery.
- Replace, reprocess, and delete sources without leaving active stale chunks.

### Retrieval and answers

- Parse and split sources while retaining headings and source locations.
- Produce embeddings through Spring AI.
- Store relational metadata and vectors in PostgreSQL with pgvector.
- Retrieve with realm and access constraints inside the database query.
- Let an interchangeable chat-model adapter select passage identifiers; build literal excerpts from authorized originals in the backend.
- Return citations and a structured `INSUFFICIENT_EVIDENCE` outcome.
- Open the exact authorized source context behind a citation.
- Distinguish approved canon from editorial proposals.

### Web application

- Register and authenticate through Keycloak Authorization Code with PKCE `S256`.
- Create and switch realms without exposing internal identifiers.
- Administer invitations, spoiler groups, grants, sources, and questions in Spanish.
- Navigate and curate the authorized lore catalogue without exposing internal identifiers.
- Show only viewer-visible sources and distinguish insufficient evidence from model-runtime failure.
- Search visible source titles and filenames, read complete authorized documents, and follow atlas relations to their entities.
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

## Outside the first release

- Native desktop or mobile user interface
- PDF, OCR, images, audio, and video
- Social login and production identity hosting
- Password storage in the application
- Microservices
- Kafka, RabbitMQ, or another broker
- Kubernetes or OpenShift deployment
- Distributed transactions
- Neo4j or a separate graph database
- GraphRAG
- Automatic entity and relation extraction
- Reranking
- Hybrid keyword/vector retrieval as a default feature; the experimental implementation remains disabled
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

Ask the same spoiler question through each account. The Game Master and the player with access should be able to open their citations. The other player should receive an insufficient-evidence result that does not reveal the hidden source.

Check this scenario through integration tests, the RAG evaluation set and the [browser/recovery procedures](../../OPERATIONS.md). Run Playwright separately from the legacy `verify-m8.2.ps1` command.

## Change control

Before expanding this scope, record:

1. The user or engineering need and why the current design cannot meet it.
2. An ADR if the change affects architecture or security.
3. Acceptance criteria and a place in the roadmap.
4. What will be deferred or removed to keep the first release manageable.
