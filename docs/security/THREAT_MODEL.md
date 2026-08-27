# Threat Model

- **Status:** M5.1 grounded-answer and model-evaluation controls implemented
- **Date:** 2026-08-27
- **Scope:** MVP local deployment and REST API

This is a living threat model. Controls listed as planned are requirements for the milestone that introduces the affected asset; they are not claims about code that does not yet exist.

## Security objectives

1. Prevent knowledge from crossing realm boundaries.
2. Prevent players from retrieving or inferring unrevealed content.
3. Preserve source, canon, and citation integrity.
4. Treat uploaded documents, user prompts, and model output as untrusted.
5. Limit the effect of prompt injection even when prevention is imperfect.
6. Avoid secret, token, prompt, and lore disclosure through logs or configuration.
7. Keep resource consumption bounded on local hardware.

## Assets

- Realm source documents and immutable versions
- Private Game Master and spoiler content
- Memberships, roles, and explicit grants
- Canon status and provenance
- Chunks and embeddings
- Questions, retrieved context, answers, and citations
- OIDC tokens and Keycloak configuration
- Model configuration and runtime endpoints
- Audit, metric, and application logs

## Trust boundaries

1. User or future browser to the REST API
2. REST API to authenticated application use cases
3. Application to Keycloak
4. Application to PostgreSQL/pgvector
5. Application to raw-file storage
6. Application to Ollama or another model provider
7. Untrusted source text entering the ingestion and prompt pipelines
8. CI and developer configuration entering the local runtime

## Threat actors

- An unauthenticated network client
- An authenticated member probing another realm
- A player attempting to reveal spoilers
- A malicious or compromised editor uploading adversarial content
- An accidental operator exposing secrets or sensitive telemetry
- A compromised model runtime or future remote model provider
- A model influenced by direct or indirect prompt injection

## Key data flow

### Ingestion

`authenticated editor -> upload validation -> immutable storage -> parsing -> chunking -> embedding model -> authorized persistence`

### Question answering

`authenticated member -> realm membership -> effective access -> authorized vector query -> evidence gate -> model context -> output validation -> citations`

The authorization decision occurs before vector ranking. Retrieved text is labelled as untrusted evidence and receives no capability to call tools or mutate state.

## Threat register

| ID | Threat | Severity | Required controls and evidence |
|---|---|---:|---|
| T-01 | Cross-realm IDOR or enumeration | Critical | Server-derived realm scope, object-level checks, realm-qualified repository methods, non-disclosing errors, negative API and persistence tests. |
| T-02 | Spoiler or GM-only chunk enters retrieval | Critical | Relational access predicate inside the vector query, no post-retrieval security filtering, validated citations, one Game Master/two-player integration matrix. |
| T-03 | Direct prompt injection changes answer policy | High | Constrained task, structured output, no model tools, evidence gate outside the model, output validation, adversarial evaluation cases. |
| T-04 | Uploaded lore contains indirect prompt injection | High | Treat sources as untrusted data, separate and delimit instructions from evidence, retain provenance, no tool access, adversarial source fixtures, editor audit metadata. |
| T-05 | Path traversal, parser abuse, or upload denial of service | High | Generated storage identifiers, normalized display names, allowlisted types, size/chunk limits, UTF-8 policy, timeouts, storage outside served paths, malformed-file tests. |
| T-06 | Secrets or lore leak through logs and traces | High | Runtime secret injection, log redaction, no normal logging of tokens/prompts/chunks/answers, sanitized error responses, telemetry review tests. |
| T-07 | Remote or compromised model provider exfiltrates context | High | Local Ollama default, explicit provider configuration, minimal context, documented data boundary, outbound network policy when deployed. |
| T-08 | Data poisoning becomes accepted canon | High | Source provenance, editor identity, immutable versions, explicit canon promotion, AI output defaults to proposed, deletion/revocation support. |
| T-09 | Unbounded token, upload, or query consumption | Medium | Request quotas, source and question limits, maximum chunks and context tokens, timeouts, bounded output, per-operation metrics. |
| T-10 | Deleted or superseded content remains searchable | High | Active-version predicate, transactional activation, idempotent cleanup, delete/reprocess integration tests, embedding provenance. |
| T-11 | JWT accepted with wrong issuer, audience, or lifetime | High | Signature, issuer, audience, expiry, and not-before validation; deny by default; security integration tests. |
| T-12 | Model output is rendered or executed unsafely by a future UI | Medium | Output remains data, encode on render, sanitize supported Markdown, forbid model-supplied active content; revisit in the UI threat model. |

## Prompt-injection position

Prompt injection cannot be solved by a stronger system prompt. RAG itself does not eliminate direct or indirect injection. The MVP reduces impact by keeping authorization, retrieval filters, evidence sufficiency, citation validation, and all state changes outside model control.

The model receives no tools in the MVP. A compromised answer can mislead the viewer, but it cannot query extra data, change canon, send network requests, or perform privileged actions through the application.

## Privacy and observability policy

- Log identifiers, timings, counts, model names, status codes, and coarse score distributions.
- Do not log bearer tokens, raw prompts, retrieved chunks, full answers, or uploaded source content by default.
- Traces crossing a model boundary contain metadata, not lore text.
- Any opt-in diagnostic content logging must be local-only, visibly enabled, bounded, and documented.
- Evaluation fixtures contain only original demonstration lore, never private campaign data.

## Security verification plan

### M1

- Configuration secret scan
- JWT validation tests
- Container configuration review
- Module-boundary test

### M2

- Cross-realm API and repository tests
- Role and membership matrix
- Spoiler grant/revocation tests
- Non-disclosing error behavior

Implemented evidence lives in **RealmAuthorizationIntegrationTest**, **AccessPolicyTest**, the V2 Flyway migration, and **AUTHORIZATION_MODEL.md**. Execution of the PostgreSQL-backed suite remains pending until the local Docker engine is responsive.

### M3

- Upload size, type, name, encoding, and malformed-content tests
- Storage traversal tests
- Idempotent deletion and reprocessing tests

Implemented evidence lives in **SourceFileValidatorTest**, **StructuralChunkerTest**, **LocalRawSourceStorageTest**, the V3 Flyway migration, and the source lifecycle case in **RealmAuthorizationIntegrationTest**. The unit controls pass; execution of the PostgreSQL-backed case remains pending until the local Docker engine is responsive.

### M4/M5

- Assert every retrieved and cited chunk satisfies effective access
- Direct and indirect prompt-injection fixtures
- Hidden-source questions that must return `INSUFFICIENT_EVIDENCE`
- Token/context/output bounds
- Model output schema and citation validation

M4 evidence lives in **PgVectorLoreRetriever**, **ADR-006**, **RetrievalQueryTest**, and the baseline retrieval case in **RealmAuthorizationIntegrationTest**. Authorization is part of the materialized SQL candidate set before vector distance is evaluated. PostgreSQL execution remains pending until Docker Desktop is responsive.

M5 evidence lives in **EvidenceGate**, **AnswerValidator**, **SpringAiGroundedAnswerModel**, **ADR-007**, the deterministic Spanish baseline gate test, and the answer cases compiled into **RealmAuthorizationIntegrationTest**. Direct injection, weak evidence, malformed output, unknown citations, and ungrounded claims all fail closed without an answer. M5.1 adds provider-native JSON Schema and a real-model evaluation where any restricted-fact leak or unexpected answer is a hard blocker. Container-backed and target-hardware execution remain pending until Docker Desktop or native Ollama is responsive.

## Residual risks

- A model can still generate misleading text from authorized evidence.
- Similarity thresholds can reject valid evidence or accept weak evidence.
- Local machine users with filesystem or database access are outside application-level isolation.
- Keycloak development credentials are not production credentials.
- A future browser client or remote provider will introduce new trust boundaries and requires this model to be revised.

## References

- [OWASP LLM01:2025 Prompt Injection](https://genai.owasp.org/llmrisk/llm01-prompt-injection/)
- [OWASP LLM08:2025 Vector and Embedding Weaknesses](https://genai.owasp.org/llmrisk/llm082025-vector-and-embedding-weaknesses/)
- [OWASP API1:2023 Broken Object Level Authorization](https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/)
- [Spring Security OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
