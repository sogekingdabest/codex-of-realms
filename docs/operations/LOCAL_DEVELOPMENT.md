# Local development

## Prerequisites

- Java 21 for running Maven directly
- Docker Desktop with Docker Compose for the complete stack and integration tests
- At least 8 GB of free system memory recommended while all services run

Maven does not need to be installed globally; the repository includes the Maven Wrapper.

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

The integration test starts an isolated PostgreSQL/pgvector container, applies Flyway, checks the vector extension, and verifies the public and protected HTTP boundaries. Docker must be running.

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
- OpenAPI document: <http://localhost:8080/v3/api-docs>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Keycloak: <http://localhost:8180>
- Ollama API: <http://localhost:11434>

The imported Keycloak realm is **codex-of-realms**. The admin username is **admin**; its password comes only from **.env**. M1 intentionally creates no demo users and enables no password grant. Interactive users and realm memberships are introduced in M2.

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

That is expected in M1. Startup never pulls a model, and Spring AI model auto-configuration is disabled. Model installation and evaluated defaults arrive in later milestones.
