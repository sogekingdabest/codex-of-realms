# Realm authorization model

## Security boundary

Keycloak authenticates an external identity. Codex of Realms owns every authorization decision:

1. A validated JWT supplies **iss** and **sub**.
2. The API synchronizes that pair to a local **codex_user**.
3. A realm operation requires an active local membership.
4. Privileged commands derive the caller role from PostgreSQL.
5. Access-policy reads apply classification and spoiler grants in the SQL query.

No realm role, membership, realm identifier, or spoiler grant is trusted from client-supplied claims or request bodies.

## Roles

| Capability | Owner | Editor | Player |
|---|---:|---:|---:|
| Read a joined realm | Yes | Yes | Yes |
| Manage memberships | Yes | No | No |
| Create access policies | Yes | Yes | No |
| Manage spoiler grants | Yes | Yes | No |
| Read PUBLIC | Yes | Yes | Yes |
| Read GM_ONLY | Yes | Yes | No |
| Read SPOILER without a grant | Yes | Yes | No |
| Read SPOILER with a matching grant | Yes | Yes | Yes |

At least one active owner must remain. Membership changes lock the realm row and repeat the role check inside the transaction so concurrent owner changes cannot bypass this invariant.

## Non-disclosing behavior

An authenticated caller receives the same **404 Not Found** response when:

- the realm or policy does not exist;
- the caller is not an active member;
- the caller lacks the required realm role;
- the caller cannot view the requested access policy;
- a membership or grant target does not belong to the realm.

Realm listing is always derived from the caller's active memberships. The API has no global realm-listing or user-listing endpoint.

## Persistence controls

- **codex_user** uniquely identifies an external user by **(issuer, subject)**.
- **realm_membership** is unique by **(realm_id, user_id)**.
- **access_policy** always belongs to one realm.
- **access_grant** carries **realm_id** and uses composite foreign keys to both its policy and membership.
- PostgreSQL therefore rejects a cross-realm grant even if application validation regresses.
- Removing a membership deletes its spoiler grants before deactivation, preventing access from silently returning after reactivation.

## API surface

| Method and path | Purpose |
|---|---|
| **GET /api/v1/me** | Synchronize the JWT identity and return the local user plus joined realms. |
| **POST /api/v1/realms** | Create a realm and its initial owner membership. |
| **GET /api/v1/realms** | List only realms visible to the current member. |
| **GET /api/v1/realms/{realmId}** | Read one joined realm. |
| **PUT /api/v1/realms/{realmId}/memberships/{userId}** | Add, reactivate, or change a membership as an owner. |
| **DELETE /api/v1/realms/{realmId}/memberships/{userId}** | Revoke a membership as an owner. |
| **POST /api/v1/realms/{realmId}/access-policies** | Create a policy as an owner or editor. |
| **GET /api/v1/realms/{realmId}/access-policies/{policyId}** | Read a policy only when effective access permits it. |
| **PUT .../access-policies/{policyId}/grants/{userId}** | Grant a spoiler policy to an active player. |
| **DELETE .../access-policies/{policyId}/grants/{userId}** | Revoke a spoiler grant. |

Users must call **GET /api/v1/me** once before an owner can add their returned local user identifier to a realm. This avoids a global identity lookup endpoint.

## Demonstration identities

The Keycloak import contains **gm-demo**, **nara-demo**, and **ivo-demo**, without passwords or stored credentials. A local administrator sets temporary passwords through Keycloak before using the interactive authorization-code flow. Realm roles and memberships are then created through the Codex API, not in Keycloak.
