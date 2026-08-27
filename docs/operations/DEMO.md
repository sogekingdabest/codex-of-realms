# Reviewer demo

This path demonstrates the system without private setup knowledge. It uses only repository fixtures and locally chosen development passwords.

## Prerequisites

- Eclipse Temurin JDK 21 available as `java`
- Docker Desktop running
- PowerShell 7 or Windows PowerShell 5.1
- At least 16 GB system RAM recommended for the full local-model stack

Copy `.env.example` to `.env` and set strong local-only values for `POSTGRES_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, and `GRAFANA_ADMIN_PASSWORD`. Do not commit `.env`.

## One-command verification

From the repository root:

```powershell
.\scripts\demo.ps1
```

This runs all deterministic tests, starts a disposable PostgreSQL/pgvector through Testcontainers, produces the M7 report and module documents, and validates the Compose definition. It does not start the long-running stack or download models.

## Start the application

Prepare the explicitly configured models once:

```powershell
ollama pull bge-m3
ollama pull qwen3:4b
```

Then run:

```powershell
.\scripts\demo.ps1 -StartStack
```

Add `-WithObservability` to include Prometheus and Grafana. Add `-SkipVerification` on subsequent starts when the same revision has already passed.

## Suggested review

1. Confirm `/actuator/health` is `UP` and inspect the API in Swagger UI.
2. Open Keycloak at <http://localhost:8180>, use the local admin password, and inspect the imported `codex-of-realms` realm and client.
3. Exercise the realm, access-policy, source, retrieval, question, and catalogue endpoints in their lifecycle order.
4. Inspect the deterministic access matrix for `player_oren`, `player_tala`, and `gm_ines`: Oren sees public lore, Tala also receives the spoiler grant, and Inés can access GM-only lore. These names are stable test actors; create equivalent interactive memberships for the imported `gm-demo`, `nara-demo`, and `ivo-demo` users when exercising the API manually.
5. Ask an unsupported or injection-style question and verify `INSUFFICIENT_EVIDENCE` has no answer or citations.
6. Inspect `backend/target/portfolio-reports/`, `backend/target/spring-modulith-docs/`, and the optional Grafana dashboard.

The normal CI path uses signed test JWTs and deterministic model doubles. Interactive user passwords are deliberately not stored in the imported realm; create or reset local test-user passwords in Keycloak when you want browser login rather than API fixtures.
