# ADR-014: Expose manual canon curation in the first-party web client

- **Status:** Accepted
- **Date:** 2026-08-29

## Context

M6 established access-aware entities, relations, provenance snapshots, and append-only promotion history, but those
capabilities were available only through the API. A curator still needed Swagger and internal UUIDs, while a player
could not navigate the structured knowledge they were allowed to see. The API accepted evidence chunk identifiers but
did not provide a safe browser workflow for discovering them.

## Decision

- Add an **Atlas del canon** to the existing React application instead of creating another repository or service.
- Keep creation, revision, retirement, and promotion manual and restricted to `OWNER` and `EDITOR` members.
- Show `PLAYER` members a read-only catalogue filtered by the existing SQL authorization predicates.
- Add an editor-only endpoint for chunks from the active `READY` source version so evidence can be selected without
  exposing arbitrary or historical chunk identifiers.
- Require selected evidence to share the catalogue claim's access policy, preserving the existing server invariant.
- Present `PROPOSED` and `CANON` as explicit states; editing a canonical record returns it to `PROPOSED`, and only a
  separate human action promotes it again.
- Reuse the authorized source-content dialog for catalogue evidence.

## Consequences

- The structured catalogue becomes a useful product surface without a language model, graph database, or automatic
  extraction pipeline.
- Realm and spoiler boundaries remain enforced by the backend even if a client is modified.
- Source-fragment discovery is intentionally unavailable to players; their durable evidence view is the safe snapshot
  attached to an authorized catalogue record.
- Retiring a source preserves its immutable evidence snapshot, but the complete raw source can no longer be opened.
- Large catalogues may eventually need pagination and richer exploration, but M9 does not justify a separate frontend
  repository, service, or database.
