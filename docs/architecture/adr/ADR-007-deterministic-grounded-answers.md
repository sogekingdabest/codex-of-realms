# ADR-007: Gate and validate every generated answer outside the model

- **Status:** Partially superseded: model output now contains passage identifiers, and the server builds literal excerpts. See the [current decision summary](../DECISIONS.md) and [answer contract](../../../OPERATIONS.md#extractive-answer-contract). The dated decision below preserves the earlier generated-claim design.
- **Date:** 2026-08-27

## Context

A visible search result may still be poor evidence for the question. The model can also cite a nonexistent fragment, follow instructions in a source or add unsupported facts. The application therefore needs checks before and after generation.

The initial machine has 16 GB of RAM and an RTX 3060 Mobile with 6 GB of VRAM. The Spanish demo therefore needs a compact multilingual model that can run alongside `bge-m3`.

## Decision

- Use `qwen3:4b` through Spring AI's provider-neutral `ChatModel` as the first local chat baseline.
- Keep model download explicit and configure Ollama never to pull models during application startup.
- Expose a public `LoreSearch` facade so `qa` consumes already-authorized evidence without reaching into `lore` internals.
- Apply a deterministic pre-generation gate for direct injection patterns, minimum cosine similarity, and significant-term coverage.
- Send system instructions separately from a JSON user payload containing the question and delimited untrusted evidence.
- Give the model no tools and require structured claims with evidence ranks.
- Validate claim bounds, lexical grounding, citation existence, citation uniqueness, and output size in application code.
- Convert missing models, malformed output, invalid citations, unsupported claims, and gate failures to the same `INSUFFICIENT_EVIDENCE` shape.
- Render citation markers next to each accepted claim and return immutable source/version/chunk provenance.

## Consequences

The pipeline can refuse answerable questions. Calibrate its configurable thresholds against the versioned baseline. Lexical checks catch some unsupported claims, but fuller grounding evaluation remains part of M7.

Model output never becomes canon. A future provider or browser runtime can implement the same `GroundedAnswerModel` port without changing the authorization, gate, or citation contracts.

## Rejected alternatives

- Let the model decide whether evidence is sufficient, because the decision would be nondeterministic and prompt-injectable.
- Accept free-form prose and extract citations afterward, because unsupported text may already have escaped validation.
- Send all realm chunks and ask the model to respect access labels, because a model is not an authorization system.
- Select a larger model as the default, because it would reduce local reproducibility on the target 6 GB GPU.
