# ADR-014: Expose manual canon curation in the first-party web client

- **Status:** Accepted
- **Date:** 2026-08-29

## Context

M6 added entities, relations, evidence snapshots and promotion history through the API. Curators still needed Swagger and UUIDs, and players had no interface for browsing the catalogue. Evidence selection also needed a way to find eligible chunks in the browser.

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

- Users can browse and curate the catalogue without running a language model.
- Realm and spoiler boundaries remain enforced by the backend even if a client is modified.
- Source-fragment discovery is intentionally unavailable to players; their durable evidence view is the safe snapshot
  attached to an authorized catalogue record.
- Retiring a source preserves its immutable evidence snapshot, but the complete raw source can no longer be opened.
- Large catalogues may eventually need pagination and richer exploration, but M9 does not justify a separate frontend
  repository, service, or database.
