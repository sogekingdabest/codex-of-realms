# Source ingestion

> Historical record. See [current documentation](../../README.md) for setup and behavior.

M3 adds source management at `/api/v1/realms/{realmId}/sources`. Requests use the realm API's Keycloak bearer token. Every operation checks that the caller is still an active `OWNER` or `EDITOR`.

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/sources` | Create a logical source and its first version. |
| `PUT` | `/sources/{documentId}` | Replace content or policy with a new immutable version. |
| `POST` | `/sources/{documentId}/reprocess` | Re-index only when the pipeline fingerprint changed. |
| `GET` | `/sources` | List active ready sources. |
| `GET` | `/sources/{documentId}` | Inspect active version and embedding provenance. |
| `DELETE` | `/sources/{documentId}` | Retire metadata, remove all chunks, and delete raw versions. |

Create uses `multipart/form-data` fields `title`, `accessPolicyId`, and `file`. Replace uses `accessPolicyId` and `file`. Swagger UI exposes the exact generated contract.

## Accepted input

- `.md`, `.markdown`, and `.txt`
- Strict UTF-8 text without null or unsupported control characters
- At most 1 MiB by default, enforced by both the HTTP multipart layer and the application validator
- `text/markdown`, `text/plain`, or a generic binary declaration when the validated extension and content are textual

Client filenames are display metadata only. Storage paths contain server-generated realm, document, and version UUIDs.

## Version and failure behavior

Raw bytes and their SHA-256 checksum never change. Replacement activates a new `READY` version and retires the predecessor atomically. A processing failure receives a safe failure code and cannot displace the prior active version. Repeating an unchanged replacement or reprocess returns the current version, and repeating deletion is safe.

Internally, `content.application` is split into `ingestion`, `source`, and `evidence`. Ingestion uses short metadata
transactions for version creation and the `VALIDATED → PROCESSING → READY` transitions. Raw storage, validation
after loading a version, and embedding calls run outside those transactions. If one fails, a compensating metadata
transaction marks the candidate version `FAILED`. Source reads and file deletion also occur after their metadata
query or retirement transaction has completed.

Markdown headings define the first split boundary. Oversized sections are divided into overlapping windows with exact character offsets. Each chunk stores its version, ordinal, heading, offsets, vector, model, provider, dimension, and pipeline fingerprint.

Failures are RFC Problem Details with stable codes: `source.invalid` (400), `source.unavailable` (404),
`source.upload_too_large` (413), and `source.embedding_unavailable` (503). Missing and inaccessible sources share
`source.unavailable` so the API does not disclose whether a resource exists.

## Local model

Compose enables the Ollama embedding provider and selects `bge-m3`, while automatic downloads remain disabled. Prepare the model as described in [Local development](../../operations/LOCAL_DEVELOPMENT.md). Automated tests use a deterministic three-dimensional embedding double and never depend on Ollama or network access.
