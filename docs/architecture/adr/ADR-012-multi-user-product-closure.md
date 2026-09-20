# ADR-012: Close the multi-user product flow inside the modular monolith

- **Status:** Accepted
- **Date:** 2026-08-28

## Context

After M8, owners still needed Keycloak administration, Swagger and internal UUIDs to set up a group. Policies lacked names, Keycloak data disappeared with its container, and players could neither list their sources nor open cited passages. This milestone completes those everyday tasks in the UI.

## Decision

- Keep the backend and frontend in the existing monorepo and modular monolith.
- Create `PUBLIC` and `GM_ONLY` policies with every realm and give every policy a realm-unique display name.
- Invite `EDITOR` and `PLAYER` members by normalized email. A pending invitation becomes an active membership when
  the matching OIDC identity first synchronizes; existing local users are admitted immediately.
- Keep Keycloak as the identity authority and application memberships as the authorization authority. Keycloak
  registration creates an identity but never grants access to an existing realm without an application invitation.
- Let members list only sources whose policies they can currently access. Mutations remain editor-only.
- Expose the active immutable source version behind the same access predicate so citations can open exact evidence.
- Report model capability and safe answer-failure categories separately from insufficient evidence.
- Persist Keycloak in a dedicated PostgreSQL schema inside the existing local PostgreSQL service. Back up the whole
  database and raw-source volume as one operational unit.

## Consequences

- A realm can be administered from the first-party UI without copying application UUIDs.
- Registration is available locally, but it does not bypass realm membership or spoiler grants.
- Email becomes invitation-routing data and is visible only to authorized realm administrators.
- Recreating the Keycloak container no longer removes credentials or realm configuration after the first import.
- Citation content remains unavailable when its version is no longer active or the viewer loses access.
- The local deployment still uses Keycloak development mode and localhost HTTP; internet-facing production hosting
  requires a separate hardened deployment decision.
