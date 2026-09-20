# Realm authorization model

## Security boundary

Keycloak establishes who is making the request. The application then checks what that person can do in the requested campaign:

1. A validated JWT supplies **iss** and **sub**.
2. The API synchronizes that pair to a local **codex_user**.
3. A realm operation requires an active local membership.
4. Privileged commands derive the caller role from PostgreSQL.
5. Access-policy reads apply classification and spoiler grants in the SQL query.

A realm ID in a request selects the target; it does not grant access. Roles, memberships and spoiler grants are read from the database.

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

Realm lists contain only the caller's active memberships. There is no global realm or user directory.

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
| **GET /api/v1/realms/{realmId}/access-policies** | List only policies effectively visible to the current member. |
| **GET /api/v1/realms/{realmId}/access-policies/{policyId}** | Read a policy only when effective access permits it. |
| **PUT .../access-policies/{policyId}/grants/{userId}** | Grant a spoiler policy to an active player. |
| **DELETE .../access-policies/{policyId}/grants/{userId}** | Revoke a spoiler grant. |

An owner invites a person by email and shares the application URL. The invitation stays pending until the recipient makes a request with the matching email and a current boolean `email_verified=true` JWT claim. A stored verification value cannot activate it. The application does not send invitation emails.

Owners can also administer existing identities through the membership API.

Invitation routes under **/api/v1/realms/{realmId}/invitations** support creation and listing, plus revocation by invitation ID. All require the owner role. Invited roles are `EDITOR` or `PLAYER`; ownership changes use membership administration.

## Demonstration identities

The Keycloak import contains **gm-demo**, **nara-demo**, and **ivo-demo**, without passwords or stored credentials, as optional fixtures. For normal use, register from the login page. Joining an existing campaign still requires application membership, managed through the Codex UI/API.
