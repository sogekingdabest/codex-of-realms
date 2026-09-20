# Local development

## Requirements

- Docker with Compose for the application and PostgreSQL integration tests.
- Java 21 for Maven commands; the repository includes the wrapper.
- Node.js 24 and npm for frontend commands.
- PowerShell for the repository's operational scripts, including backup and evaluation.

Development uses a machine with 16 GB RAM and a 6 GB NVIDIA GPU. Expect inference speed and model-loading time to vary on other hardware.

## Startup

Copy `.env.example` to `.env` without overwriting an existing configuration. Set the PostgreSQL and Keycloak passwords; set the Grafana password if using observability.

From the repository root:

```sh
docker compose up -d --build --wait
docker compose exec ollama ollama pull bge-m3
docker compose exec ollama ollama pull qwen3.5:4b
```

The downloads go into the Compose Ollama volume. A native Ollama installation uses a separate store. If you changed the model defaults, pull those tags instead, then reload the application.

For an NVIDIA-enabled Docker installation, use the GPU override when starting the stack:

```sh
docker compose -f compose.yaml -f compose.gpu.yaml up -d --build --wait
```

For an existing Keycloak realm, run `./scripts/configure-email-verification.ps1` after startup. Import at startup does not update existing realms.

## Local endpoints

| Service | URL |
|---|---|
| Web application | http://localhost:5173 |
| Email verification inbox | http://localhost:8025 |
| API readiness | http://localhost:8080/actuator/health/readiness |
| OpenAPI | http://localhost:8080/v3/api-docs |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Keycloak | http://localhost:8180 |
| Ollama | http://localhost:11434 |

Register through the application, verify your email in Mailpit and finish setting your password. Owners share the application URL with invitees; creating an invitation sends no email. Follow the [demo guide](DEMO.md) to prepare a campaign.

If you change an authentication address, update the issuer, browser URL, Keycloak redirects and Nginx CSP together. Changing just the published port will leave them inconsistent.

## Checks

From `backend/`, with Docker running:

```sh
./mvnw --batch-mode --no-transfer-progress verify
```

Use `.\mvnw.cmd` on Windows. From `frontend/`:

```sh
npm ci
npm run verify
```

These checks use model doubles. Continue with [browser and recovery checks](../../OPERATIONS.md#reproducible-acceptance) when those workflows change. [Code quality](CODE_QUALITY.md) covers optional SonarQube setup.

`scripts/demo.ps1` checks the backend and Compose and can start the local stack; it does not populate demo data or run the complete browser suite. `scripts/verify-m8.2.ps1` retains its historical name and combines backend/frontend verification with a running-stack check and isolated backup verification; run browser acceptance separately.

## Data and troubleshooting

Stop with `docker compose down` to keep database, source and model volumes. See [backup and restore](BACKUP_AND_RESTORE.md) before replacing an installation. Removing volumes deletes persisted data.

- **Missing password:** fill in `.env`; Compose rejects empty required credentials.
- **Docker unavailable:** start Docker and wait for `docker info` to succeed before backend tests.
- **Port conflict:** check ports 5173, 5432, 8025, 8080, 8180 and 11434. Keep identity URLs consistent if changing ports.
- **Models unavailable:** pull both configured models into the Compose store. Jobs can fail and be retried; question failures are distinct from missing evidence.
- **Wrong token issuer:** the default issuer is `http://localhost:8180/realms/codex-of-realms`, with audience `codex-api`. The internal signing-key URL is intentionally different.
- **Database password changed in .env:** an existing PostgreSQL volume retains its previous database-role password. Update it using the interactive `\password codex` command in `psql`; do not delete a populated volume to fix configuration.

[Observability](OBSERVABILITY.md) is optional and must remain on a trusted local network.
