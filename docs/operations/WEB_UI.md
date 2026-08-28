# Web UI

M8 adds the first product interface under `frontend/`. It covers the complete browser path from Keycloak login to a grounded answer with exact source citations.

## Prerequisites

- Node.js 24 LTS for direct frontend development
- The backend, PostgreSQL, Keycloak, and Ollama dependencies described in [Local development](LOCAL_DEVELOPMENT.md)
- A temporary password assigned to one of the imported Keycloak users
- `bge-m3` available before ingesting sources; a chat model is needed for answered questions

## Run with Docker Compose

From the repository root:

~~~powershell
docker compose up --build
~~~

Open <http://localhost:5173>. The browser redirects to the `codex-of-realms` Keycloak realm at <http://localhost:8180>, then returns to the UI through Authorization Code + PKCE.

The container serves static files with Nginx and proxies `/api/*` to the backend. Access and refresh tokens remain in `keycloak-js` memory and are not persisted by the application.

If Keycloak was already started with the previous port-3000 client import, recreate only its disposable local container so it imports the new exact redirect URI:

~~~powershell
docker compose rm -sf keycloak
docker compose up -d keycloak
~~~

This does not delete the PostgreSQL, Ollama, or source named volumes. The local Keycloak service currently has no persistent named volume.

## Assign a demo password

The imported users `gm-demo`, `nara-demo`, and `ivo-demo` deliberately contain no committed credentials. Open the local Keycloak administration console, sign in as `admin` with `KEYCLOAK_ADMIN_PASSWORD` from `.env`, select the **codex-of-realms** realm, open **Users**, choose a demo user, and set a temporary password under **Credentials**.

`gm-demo` is only an OIDC identity until it first calls the API. After login:

1. Create the first universe from the empty state.
2. Create a visibility policy: **Pública**, **Solo dirección**, or **Spoiler con permiso**.
3. Upload a UTF-8 Markdown or TXT source with a title and policy.
4. Ask a question. An answered result includes the document, heading, immutable version, character offsets, and model provenance; unsupported questions return a deterministic insufficient-evidence state.

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

The tests cover bearer-token API requests, safe multipart handling, realm/source presentation, and citation rendering. CI runs these checks independently of the deterministic Java suite.

## Security boundary

The UI hides upload and policy creation for players, but this is not authorization. The API still checks the authenticated membership and effective policy in its application and SQL boundaries. A user who changes the page or sends a request manually does not gain additional access.
