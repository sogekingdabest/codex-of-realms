# ADR-009: Keep structured lore manual, access-aware, and explicitly promoted

- **Status:** Accepted
- **Date:** 2026-08-28

## Context

Players need to browse characters, places and relationships as well as source passages. Automatically extracting and approving those records could turn a model mistake into canon or copy a private fact into a more widely visible entry.

Catalogue entries need the same campaign and permission checks as sources. Their evidence must also remain traceable after the supporting source is retired.

## Decision

- Keep entities and relations in the existing `lore` module and PostgreSQL system of record.
- Create every entity and relation as `PROPOSED`; only an authenticated `OWNER` or `EDITOR` can promote it to
  `CANON` through an explicit command.
- Record every promotion in an append-only audit table. Updating a canonical item returns it to `PROPOSED` while
  preserving its promotion history.
- Use the controlled entity types `CHARACTER`, `PLACE`, `FACTION`, `OBJECT`, and `EVENT`.
- Represent relation types as normalized directional identifiers so realms can use vocabulary such as
  `MIEMBRO_DE` without a schema migration for every new relation type.
- Require both relation endpoints to be active entities in the same realm. A read returns a relation only when its
  own policy and both endpoint policies are currently visible to the member.
- Permit optional source evidence only from an active chunk in the same realm and under the exact same access
  policy as the structured claim.
- Copy display-safe document, version, chunk, checksum, heading, and offset data into an immutable provenance
  snapshot. The snapshot deliberately survives later source retirement or deletion.
- Soft-delete catalogue items. An entity with active relations cannot be deleted until those relations are
  explicitly removed.

## Consequences

Canon remains a human decision, and edits cannot silently preserve an earlier canon approval. SQL applies access
control before catalogue records are materialized, including endpoint visibility for relations. Snapshot evidence
is auditable after source deletion but is no longer a live foreign key to deleted content; its checksum and immutable
identifiers make that distinction explicit.

The initial API handles individual entries and relations. List reads may issue extra evidence and audit queries per result; catalogue growth will determine when to add pagination and batching.

## Rejected alternatives

- Automatically extract and promote entities with the chat model, because generated suggestions are not canon.
- Authorize only the relation policy, because a visible edge could reveal a hidden endpoint.
- Allow evidence from a broader or unrelated policy, because the structured claim could become an access-control
  downgrade.
- Cascade-delete active relations with an entity, because it would erase claims without an explicit user command.
- Keep only live foreign keys to chunks, because source deletion would either destroy provenance or block the
  existing source lifecycle.
