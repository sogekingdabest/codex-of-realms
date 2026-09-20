# Architecture

Codex of Realms keeps the web client, backend and deployment files in one repository. The backend is a modular monolith: one Spring Boot process, with package boundaries checked by Spring Modulith and ArchUnit.

## Why one application

The project has one developer, one local deployment and workflows that often need the same database transaction. A source replacement, for example, must publish the new version and retire the old one together. Separate services would add coordination before there is a measured need to scale them independently.

PostgreSQL holds campaign records, pgvector embeddings and processing jobs. Original files live in a Docker volume. This means recovery must restore the database and files as a pair. The [decision notes](DECISIONS.md) explain the trade-offs.

## Following a document through the system

The browser sends a Markdown or TXT upload to the API. The backend checks the user's permission, validates the file, saves an immutable original and records a job. An idempotency key lets the client repeat a submission without creating duplicate work.

A worker claims the job with a database lease and generates embeddings outside long-running transactions. Publication checks the lease and the user's current permission again. If processing fails, the job can be retried while the previous published version stays readable.

For a question, the backend first restricts candidate chunks to the member's visible, active sources. It then ranks those chunks, builds candidate passages and asks the model to select identifiers. The server validates the selection and copies the original text into the answer, with offsets and citations. Access and version checks run again before the response leaves the backend.

The atlas follows a separate editorial workflow: owners and editors create entries, attach evidence and explicitly promote them to canon. Those edits do not automatically enter document retrieval.

## Login and campaign permissions

Keycloak handles registration and login. The React client uses Authorization Code with PKCE and keeps tokens in memory. The API validates each JWT and maps it to a local user.

Campaign membership, owner/editor/player roles and spoiler grants belong to the application database. For example, a valid login alone does not let a player read a Game Master's note. Invitations need a matching verified-email claim on the recipient's request. The [authorization guide](../security/AUTHORIZATION_MODEL.md) lists the rules and endpoints.

## Components

| Component | Job |
|---|---|
| React, TypeScript and Vite | Campaign library, atlas, questions and administration |
| Nginx | Serve the built client and proxy API requests |
| Java 21 and Spring Boot | Use cases, permissions and REST API |
| PostgreSQL and pgvector | Campaign state, jobs and vector search |
| Keycloak | OpenID Connect identity provider |
| Ollama | Embeddings and passage selection |
| Mailpit | Local email-verification inbox |

Compose also runs a one-shot Keycloak schema initializer. Prometheus and Grafana are optional. Exact versions are in the build manifests and Compose files.

The [module map](MODULES.md) shows how `realm`, `content`, `lore`, `qa`, `runtime` and `shared` collaborate. For deployment constraints and remaining work, see [current limitations](../product/LIMITATIONS.md); for commands and API details, see [operations](../../OPERATIONS.md).
