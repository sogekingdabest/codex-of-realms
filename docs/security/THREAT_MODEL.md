# Threat model

This review covers the local implementation on 20 September 2026. The table lists the controls in place and the work still needed before public hosting.

## Assets and boundaries

The application protects campaign sources, spoiler grants, memberships, original files, catalogue evidence, questions and identity tokens. Data crosses separate boundaries between the browser and API, identity provider and API, database and file storage, and source text and model processing.

Keycloak validates identity, and application rules determine access. The model sees only authorized evidence and has no tools to change application state.

## Controls and remaining work

| Threat | Existing controls | Residual risk / work |
|---|---|---|
| Cross-realm access or spoiler disclosure | Database-derived membership, scoped repositories, access predicates before ranking, fresh citation checks, negative integration tests | Every new read or write path must preserve the boundary |
| Invitation accepted by the wrong identity | Pending invitations; matching current verified-email JWT required | Production registration needs controlled identity and email configuration |
| Malicious uploads | Markdown/TXT and UTF-8 validation, size limits, generated storage identifiers | Aggregate storage quotas are not implemented |
| Prompt injection or malicious model output | Untrusted evidence handling, constrained identifier output, server-built excerpts, plain-text rendering, adversarial tests | Pattern screening is incomplete; authorized source text may be misleading |
| Source replacement or permission changes during processing | Persistent jobs, leases, idempotency, short transactions and publication rechecks | History grows without pagination |
| Stale or invented citations | Active-version and access validation, original offsets and literal copying | Correctly copied evidence can still omit facts or lack relevance |
| Credential or lore disclosure | Runtime credentials, ignored local files, bounded metric labels and safe API errors | Operators must inspect logs and artifacts before sharing them |
| Resource exhaustion | Per-file/question/context/output bounds, timeouts and bounded automatic retries | Per-user request and storage quotas and deployment-wide concurrency limits remain required for public hosting |
| Data loss | Coordinated database/file backup and isolated restore verifier | A backup requires a successful recovery drill and off-machine storage |
| Network exposure | Local deployment instructions and separation of application authorization | Default Compose publishes development services; no production HTTPS/identity/network profile is supplied |

## Evidence

- [Authorization rules](AUTHORIZATION_MODEL.md).
- Backend architecture, authorization, source-job and migration tests.
- [Browser acceptance and recovery procedures](../../OPERATIONS.md).
- [Live-model evaluations](../../demo/evaluation/README.md), which do not replace authorization tests.

Local administrators with access to the database or source volumes are outside application-level isolation. Model evaluations cover a finite set of inputs; [answer limitations](../product/LIMITATIONS.md#sources-and-questions) explain how to interpret their results.

Follow [SECURITY.md](../../SECURITY.md) to report a vulnerability. The [historical threat model](../archive/security/THREAT_MODEL.md) preserves the earlier milestone analysis.
