# Demo realm: El Meridiano de Ceniza

`El Meridiano de Ceniza` is the original Spanish-language realm used to demonstrate and evaluate Codex of Realms. Its content was created specifically for this project and must remain independent from existing fantasy franchises.

The realm is intentionally small but contains enough structure to exercise:

- Public canon shared by all realm members
- Game Master-only explanations that contradict an incomplete public account
- A spoiler revealed to one player but not another
- Characters, places, factions, objects, events, and dated claims
- Questions with direct, multi-source, restricted, and absent evidence

## Demo identities

| Subject | Realm role | Explicit grants |
|---|---|---|
| `gm_ines` | `OWNER` | All realm content through privileged role |
| `player_tala` | `PLAYER` | `reveal-maela-01` |
| `player_oren` | `PLAYER` | None |
| `outsider_nuno` | Member of another realm | None |

These are stable test identifiers, not passwords or production identities. M2 may map them to non-secret users in a versioned Keycloak development realm.

## Source layout

```text
lore/public/      Canon visible to every active member of the realm
lore/gm-only/     Canon restricted to privileged Game Master roles
lore/spoilers/    Canon available through privileged role or explicit grant
evaluation/       Machine-readable expected retrieval and authorization outcomes
```

## Metadata

The Markdown front matter is a provisional ingestion contract. M3 may refine the schema through an ADR or documented migration, but these meanings must remain stable:

- `source_id`: stable logical identifier used by evaluation cases
- `realm_id`: authorization boundary
- `language`: source language
- `canon_status`: whether the source is accepted canon
- `classification`: base access classification
- `grant_id`: explicit reveal required by a spoiler source

## Content rules

- Demo lore is written in Spanish.
- Technical metadata and identifiers use English.
- A public source must not depend on hidden material to be understandable.
- A hidden source may reinterpret public lore without changing the public source.
- Evaluation questions must never require knowledge outside the versioned corpus.
- Adding a fact requires reviewing whether evaluation expectations should change.
