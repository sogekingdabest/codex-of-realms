# Demo realm: El Meridiano de Ceniza

`El Meridiano de Ceniza` is the original Spanish campaign used in demos and evaluations. Its seven sources give players a public account of the setting while keeping a different perspective for the Game Master and selected players.

The sources include three public documents, two GM-only documents and two spoilers. They cover:

- Public canon shared by all realm members
- Game Master-only explanations that contradict an incomplete public account
- A spoiler revealed to one player but not another
- Characters, places, factions, objects, events, and dated claims
- Questions with direct, multi-source, restricted, absent, and adversarial evidence

## Demo identities

| Subject | Realm role | Explicit grants |
|---|---|---|
| `gm_ines` | `OWNER` | All realm content through privileged role |
| `player_tala` | `PLAYER` | `reveal-maela-01` |
| `player_oren` | `PLAYER` | None |
| `outsider_nuno` | Member of another realm | None |

These identifiers are used by the deterministic tests; they have no associated passwords. The imported Keycloak realm contains the separate interactive placeholders `gm-demo`, `nara-demo`, and `ivo-demo`, also without committed credentials.

## Source layout

```text
lore/public/      Canon visible to every active member of the realm
lore/gm-only/     Canon restricted to privileged Game Master roles
lore/spoilers/    Canon available through privileged role or explicit grant
evaluation/       Machine-readable expected retrieval and authorization outcomes
```

## Metadata

The Markdown front matter describes the evaluation fixtures. When uploading through the application, choose visibility explicitly in the form: front matter does not create memberships, policies or spoiler grants.

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
