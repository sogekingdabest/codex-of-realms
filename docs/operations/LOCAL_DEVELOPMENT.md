# Local development

## Prerequisites

- Eclipse Temurin JDK 21 for running Maven directly
- Docker Desktop with Docker Compose for the complete stack and integration tests
- At least 8 GB of free system memory recommended while all services run

Maven does not need to be installed globally; the repository includes the Maven Wrapper.

Verify the selected runtime in a new terminal:

~~~powershell
java --version
cd backend
.\mvnw.cmd --version
~~~

Maven should report Java 21 with vendor **Eclipse Adoptium**.

## Configure secrets

From the repository root, copy **.env.example** to **.env** and set strong local-only values:

~~~powershell
Copy-Item .env.example .env
~~~

The **.env** file is ignored by Git. Compose rejects startup while either required password is empty.

## Build and test

On Windows:

~~~powershell
cd backend
.\mvnw.cmd --batch-mode --no-transfer-progress verify
~~~

On Linux or macOS:

~~~bash
cd backend
./mvnw --batch-mode --no-transfer-progress verify
~~~

The integration test starts an isolated PostgreSQL/pgvector container, applies Flyway, and verifies authorization, ingestion, retrieval, and grounded-answer outcomes with deterministic embedding and chat doubles. Docker must be running; Ollama is not used by tests.

## Prepare the M3 and M5 models

The selected baseline is `bge-m3`: a multilingual 1024-dimensional embedding model whose Ollama package is about 1.2 GB. Model pulling is deliberately never performed during application startup.

~~~powershell
docker compose up -d postgres keycloak ollama
docker compose exec ollama ollama pull bge-m3
docker compose exec ollama ollama pull qwen3:4b
~~~

This preparation is needed once per `ollama-data` volume.

## Start the complete local stack

~~~powershell
docker compose up --build
~~~

Wait until all four services report healthy:

~~~powershell
docker compose ps
~~~

Available endpoints:

- API health: <http://localhost:8080/actuator/health>
- Application metrics: <http://localhost:8080/actuator/metrics>
- OpenAPI document: <http://localhost:8080/v3/api-docs>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Keycloak: <http://localhost:8180>
- Ollama API: <http://localhost:11434>

The imported Keycloak realm is **codex-of-realms**. The admin username is **admin**; its password comes only from **.env**. The import includes **gm-demo**, **nara-demo**, and **ivo-demo** without credentials. Set temporary passwords through the local Keycloak administration console before interactive use. Direct password grants remain disabled.

Stop services without deleting their data:

~~~powershell
docker compose down
~~~

To delete the local database and Ollama volumes as well, run **docker compose down --volumes** only when that data is no longer needed.

## Run only checks that do not require Docker

~~~powershell
cd backend
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ApplicationModulesTest test
~~~

This verifies the Java build and Spring Modulith boundaries, but it is not a substitute for the complete integration suite.

## Common failures

### Docker is not running

Testcontainers reports that no valid Docker environment exists. Start Docker Desktop, wait for **docker info** to succeed, then rerun **verify**.

### Ports are already in use

The stack uses ports 5432, 8080, 8180, and 11434. Stop the conflicting process or change the host-side port in **compose.yaml**.

### Compose rejects a missing variable

Create **.env** from **.env.example** and provide both passwords. Empty credentials are rejected deliberately.

### The API cannot validate a token

Tokens used locally must have issuer **http://localhost:8180/realms/codex-of-realms** and audience **codex-api**. The application uses Keycloak's internal container address only to obtain signing keys.

### Ollama has no models

Run `docker compose exec ollama ollama pull bge-m3` and `docker compose exec ollama ollama pull qwen3:4b`. Startup intentionally never downloads models. If embeddings are disabled outside Compose (`AI_EMBEDDING_PROVIDER=none`), source processing returns HTTP 503 and records a safe failed status. If chat is disabled or unavailable, questions fail closed to `INSUFFICIENT_EVIDENCE`.
