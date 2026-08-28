# ADR-013: Use a deterministic local release gate with isolated restore verification

- **Status:** Accepted
- **Date:** 2026-08-28

## Context

M8.1 completed the multi-user product path, but its evidence was split across backend tests, frontend tests, a manual
browser smoke, and an unverified backup. Requiring a live generative model for every acceptance run would make the gate
slow, hardware-dependent, and unsuitable for CI. Restoring over the active local stack would also put developer data at
unnecessary risk.

## Decision

- Treat deterministic backend and frontend verification as the mandatory M8.2 product gate.
- Keep the existing three-identity authorization and Spanish RAG baseline as the automated proof of public,
  `GM_ONLY`, spoiler, refusal, and citation behavior.
- Verify each selected backup in a uniquely named Docker Compose project with unpublished ports and fresh volumes.
- Require restored Flyway history, immutable-source file parity, and a healthy Keycloak boot with the expected realm.
- Add database and per-source SHA-256 plus source-file count metadata to new backup manifests.
- Keep live Ollama evaluation as an explicit `-WithLiveModel` extension. A skipped live run is reported, never confused
  with a passed model benchmark.

## Consequences

- One command can verify the product contract without downloading model weights or changing the active data volumes.
- A successful database dump is no longer mistaken for a recoverable backup.
- The deterministic gate proves authorization and orchestration, but it does not promote a chat model; M5.1 still
  requires the reviewed three-run comparison on the target hardware.
- Docker is required for PostgreSQL/Testcontainers and restore verification.
