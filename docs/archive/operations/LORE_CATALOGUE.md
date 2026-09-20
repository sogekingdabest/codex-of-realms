# Lore catalogue operations

> Historical record. See [current documentation](../../README.md) for setup and behavior.

M6 provides the catalogue API at `/api/v1/realms/{realmId}/catalogue`. M9 adds its browser interface,
**Atlas del canon**. Bearer authentication and an active realm membership are always required. `OWNER` and
`EDITOR` members mutate and promote records; `PLAYER`
members only read records allowed by their effective access.

## Entities

Supported types are `CHARACTER`, `PLACE`, `FACTION`, `OBJECT`, and `EVENT`.

| Method | Path | Meaning |
|---|---|---|
| `POST` | `/entities` | Create a proposed entity |
| `GET` | `/entities?type=&canonStatus=` | List visible entities with optional filters |
| `GET` | `/entities/{entityId}` | Read one visible entity |
| `PUT` | `/entities/{entityId}` | Replace editable fields and return the entity to `PROPOSED` |
| `POST` | `/entities/{entityId}/promotion` | Promote the current proposal to `CANON` |
| `DELETE` | `/entities/{entityId}` | Retire an entity that has no active relations |

Example creation body:

~~~json
{
  "type": "CHARACTER",
  "displayName": "Nara Vey",
  "aliases": ["La Cartógrafa"],
  "description": "Cartografía las rutas de Lumbrevela.",
  "accessPolicyId": "00000000-0000-0000-0000-000000000000",
  "evidenceChunkIds": ["00000000-0000-0000-0000-000000000000"]
}
~~~

Aliases are trimmed, deduplicated case-insensitively, and cannot repeat the display name. Source evidence is
optional. Every supplied chunk must be active, belong to the same realm, and use exactly the entity's access policy.

Curators discover eligible evidence through
`GET /api/v1/realms/{realmId}/sources/{documentId}/chunks`. The endpoint requires `OWNER` or `EDITOR`, returns chunks
only from the active `READY` version, and never weakens catalogue visibility. The web client lists chunks only for
sources whose policy matches the claim being edited.

## Relations

Relations are directional. The source and target are fixed after creation; edit the claim type, description,
policy, or evidence with `PUT`.

| Method | Path | Meaning |
|---|---|---|
| `POST` | `/relations` | Create a proposed directional claim |
| `GET` | `/relations?entityId=&canonStatus=` | List visible relations with optional filters |
| `GET` | `/relations/{relationId}` | Read one visible relation |
| `PUT` | `/relations/{relationId}` | Update the claim and return it to `PROPOSED` |
| `POST` | `/relations/{relationId}/promotion` | Promote the current claim to `CANON` |
| `DELETE` | `/relations/{relationId}` | Retire the claim |

Example creation body:

~~~json
{
  "sourceEntityId": "00000000-0000-0000-0000-000000000000",
  "targetEntityId": "00000000-0000-0000-0000-000000000000",
  "relationType": "vive en",
  "description": "Nara mantiene su taller en Lumbrevela.",
  "accessPolicyId": "00000000-0000-0000-0000-000000000000",
  "evidenceChunkIds": []
}
~~~

The server normalizes the type to `VIVE_EN`. A relation is returned only when the viewer can access the relation
policy and the policies of both endpoint entities. Cross-realm endpoints and self-relations are rejected.

## Canon and provenance

Creation always returns `PROPOSED`. Promotion records the acting user and timestamp in both the current view and an
append-only `promotionHistory`. Updating a canonical record makes it proposed again without deleting its earlier
promotion history.

`sourceEvidence` is a display-safe immutable snapshot containing source document, version and chunk identifiers,
title, checksum, heading, and offsets. It remains available if the original source is later retired or deleted. The
creator/updater and promotion audit are human provenance even when no source evidence is attached.

The evidence snapshot survives source retirement, but opening the complete raw source later still requires an active,
authorized source version. The snapshot remains the durable audit record when that raw content is no longer available.

Unknown, inaccessible, and cross-realm records all use the same non-disclosing `404` response. Deleting an entity
with active relations returns `409`; delete the relations explicitly first.

## Verification

From `backend` with Docker running:

~~~powershell
.\mvnw.cmd verify
~~~

The catalogue acceptance case applies Flyway V5 to PostgreSQL and verifies role restrictions, endpoint visibility,
cross-realm rejection, canon transitions, promotion history, evidence snapshots, deletion behavior, and OpenAPI
discovery.

Frontend component tests additionally verify the read-only player atlas, evidence-backed editor creation, and explicit
human promotion. See [Canon workspace](CANON_WORKSPACE.md) for the browser workflow and product boundaries.
