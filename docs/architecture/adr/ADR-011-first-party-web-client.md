# ADR-011: First-party web client in the monorepo

- **Status:** Accepted
- **Date:** 2026-08-28

## Context

The backend now exposes the complete authenticated path from realm membership to source ingestion and grounded answers, but exercising it through Swagger does not represent the intended product. M8 needs a first browser client without weakening the existing object-level authorization model or creating a second release process prematurely.

A browser application cannot protect a client secret. It also needs a predictable way to reach the API in local development and in the Compose stack without broad cross-origin configuration.

## Decision

- Keep the web application in `frontend/` in the existing repository. Backend, web client, Keycloak import, Compose topology, API contracts, and CI remain one versioned product.
- Use React 19 with TypeScript and Vite on Node.js 24 LTS. The client is a focused single-page application; no router or global state library is introduced until navigation or state complexity requires one.
- Use Keycloak's official `keycloak-js` adapter as a public OIDC client with Authorization Code flow and PKCE `S256`. Tokens remain in adapter memory and are refreshed shortly before authenticated API requests. They are never copied to browser storage.
- Configure exact local redirect URIs and web origins for port 5173. Keep direct password grants and implicit flow disabled.
- Proxy `/api` through Vite during development and through Nginx in Compose. The browser therefore calls the API on its own origin while Keycloak remains the explicit identity origin at port 8180.
- Treat UI role checks only as affordances. The backend continues to enforce realm, policy, source, and retrieval authorization for every request.
- Keep model execution server-side. WebLLM and LiteRT-LM remain a later, separately evaluated capability.

The adapter choices follow the [Keycloak JavaScript adapter guidance](https://www.keycloak.org/securing-apps/javascript-adapter), including a public client, specific redirect URIs, standard flow, PKCE, in-memory tokens, and token renewal before requests.

## Consequences

- A contributor can change an API contract and its consumer atomically and validate both in one CI run.
- The production-like local client is available at `http://localhost:5173`; port 3000 remains free for optional Grafana.
- Deployments at a different origin must rebuild the static bundle with the corresponding Vite variables and update the exact Keycloak redirect URI and web origin.
- The first slice intentionally handles only the primary flow: session, realm creation/selection, access-policy bootstrap, source upload/listing, grounded questions, citations, and logout.
- The client does not duplicate authorization logic. A manipulated browser still reaches the same fail-closed backend boundaries.

## Alternatives rejected

- **Separate frontend repository:** adds coordination, versioning, and CI overhead before the client has an independent team or release lifecycle.
- **Implicit OIDC flow:** exposes tokens through browser-visible redirect data and provides worse token lifecycle behavior.
- **Persist tokens in local or session storage:** increases the impact of script injection and conflicts with the adapter's in-memory design.
- **Broad backend CORS:** unnecessary for the local topology because both development and containerized clients proxy the API.
- **Server-rendered Java templates:** couples presentation to the backend and makes the future browser-model experiment harder to isolate.
