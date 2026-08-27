# Source ingestion

M3 exposes editor-only source management below `/api/v1/realms/{realmId}/sources`. Authentication uses the same Keycloak bearer token as the realm API, and every operation rechecks active `OWNER` or `EDITOR` membership.

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

Markdown headings define the first split boundary. Oversized sections are divided into overlapping windows with exact character offsets. Each chunk stores its version, ordinal, heading, offsets, vector, model, provider, dimension, and pipeline fingerprint.

## Local model

Compose enables the Ollama embedding provider and selects `bge-m3`, while automatic downloads remain disabled. Prepare the model as described in [Local development](LOCAL_DEVELOPMENT.md). Automated tests use a deterministic three-dimensional embedding double and never depend on Ollama or network access.
