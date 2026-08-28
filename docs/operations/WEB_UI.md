# Web UI

M8.1 closes the multi-user browser path from Keycloak registration to invitations, spoiler grants, grounded answers,
and exact source evidence.

## Prerequisites

- Node.js 24 LTS for direct frontend development
- The backend, PostgreSQL, Keycloak, and Ollama dependencies described in [Local development](LOCAL_DEVELOPMENT.md)
- `bge-m3` available before ingesting sources; a chat model is needed for answered questions

## Run with Docker Compose

From the repository root:

~~~powershell
docker compose up --build
~~~

Open <http://localhost:5173>. The browser redirects to the `codex-of-realms` Keycloak realm at <http://localhost:8180>, then returns to the UI through Authorization Code + PKCE.

The container serves static files with Nginx and proxies `/api/*` to the backend. Access and refresh tokens remain in `keycloak-js` memory and are not persisted by the application.

Keycloak stores its tables in the `keycloak` schema of the persistent PostgreSQL volume. Recreating the Keycloak
container no longer removes users, passwords, or realm configuration.

If upgrading an older checkout whose Keycloak container used disposable storage, recreate that container once so the
new PostgreSQL-backed service imports the realm:

~~~powershell
docker compose rm -sf keycloak
docker compose up -d keycloak
~~~

This does not delete PostgreSQL, Ollama, or source data. Existing credentials from the old disposable Keycloak
container cannot be migrated because they were never stored outside that container.

## Register and invite users

Use **Register** on the Keycloak login page to create the first owner account. Registration creates only an identity;
it never grants access to another user's realm. The first authenticated user creates a realm in the application and
can then invite editors or players using the exact email with which they register.

The imported users `gm-demo`, `nara-demo`, and `ivo-demo` remain credential-free fixtures. They can still be used by
assigning local passwords through the Keycloak administration console, but they are no longer required for normal
browser onboarding.

`gm-demo` is only an OIDC identity until it first calls the API. After login:

1. Create the first universe; **Público** and **Solo dirección** policies are created automatically.
2. Invite another registered or future user by email as **Editor** or **Jugador**.
3. Create a named spoiler group and grant it to selected players when needed.
4. Upload a UTF-8 Markdown or TXT source with a title and policy.
5. Ask a question and open any citation to inspect the authorized source context.

The header notice reports whether the configured embedding and chat models are installed. A model outage is shown as a
runtime problem rather than being presented as missing lore.

## Run the frontend directly

Keep the backend and Keycloak available on ports 8080 and 8180, then run:

~~~powershell
cd frontend
npm ci
npm run dev
~~~

Vite listens on <http://localhost:5173> and proxies `/api` to <http://localhost:8080>. Defaults can be overridden before starting or building:

| Variable | Default |
|---|---|
| `VITE_API_BASE_URL` | `/api/v1` |
| `VITE_KEYCLOAK_URL` | `http://localhost:8180` |
| `VITE_KEYCLOAK_REALM` | `codex-of-realms` |
| `VITE_KEYCLOAK_CLIENT_ID` | `codex-web` |

Values prefixed with `VITE_` are public build configuration, never secrets. When the UI or Keycloak origin changes, update the `codex-web` client redirect URI and web origin in Keycloak and the Nginx Content Security Policy as well.

## Verify

~~~powershell
cd frontend
npm run lint
npm run test
npm run build
~~~

The tests cover bearer-token API requests, safe multipart handling, owner source presentation, citation rendering, and
the player path that loads visible sources without requesting editor-only policy or membership data. They also pin the
Authorization Code + PKCE `S256` configuration and the expired-token recovery path. CI runs these checks independently
of the deterministic Java suite.

For the complete backend, frontend, running-stack, backup, and isolated-restore gate, use
[`M8.2 local product acceptance`](../evaluation/M8_2_ACCEPTANCE.md).

## Security boundary

The UI hides upload and policy creation for players, but this is not authorization. The API still checks the authenticated membership and effective policy in its application and SQL boundaries. A user who changes the page or sends a request manually does not gain additional access.
