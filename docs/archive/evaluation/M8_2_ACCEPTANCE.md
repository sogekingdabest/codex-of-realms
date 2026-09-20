# M8.2 local product acceptance

> Historical record. See [current documentation](../../README.md) for setup and behavior.

M8.2 verifies the owner/editor/player workflow, browser security, data recovery, and the known model limitation.
The checks below reproduce that milestone's acceptance run.

## Mandatory gate

Start the normal stack and run from the repository root:

~~~powershell
.\scripts\verify-m8.2.ps1
~~~

The command:

1. validates the normal and isolated-restore Compose configurations;
2. runs the Java 21 deterministic suite, including PostgreSQL-backed realm, invitation, policy, retrieval, answer, and
   citation scenarios for multiple actors;
3. installs the locked frontend dependencies and runs `npm run verify`: lint, all client tests with coverage thresholds,
   a non-empty LCOV check, and the production build, using the same command as CI;
4. confirms the six product services are running (PostgreSQL, Keycloak, Mailpit, Ollama, backend, and web);
5. creates a coordinated backup and restores it into fresh, unpublished Docker volumes;
6. boots Keycloak from the restored schema and compares the immutable-source file count and hashes.

The isolated project has a generated `codex-of-realms-restore-check-*` name and is removed with its own volumes after
the check. It never runs `down`, `restore`, or volume deletion against the active `codex-of-realms` project.

A timestamped success result is written under the ignored `demo/evaluation/results/` directory only after every required
step succeeds. A coverage failure stops the gate before backup/restore and cannot produce a success report.
For frontend-only verification without Docker, run `npm ci` and `npm run verify` from `frontend`. `npm test` is a partial
development check and does not enforce coverage. New backup manifests contain a
database SHA-256 plus each source path, size, and SHA-256; all are checked during verification. Older M8.1 manifests
remain compatible.

## Optional live-model gate

The deterministic gate does not assert that an Ollama model is installed or promote one. After downloading a candidate
deliberately, extend the same run with:

~~~powershell
.\scripts\verify-m8.2.ps1 -WithLiveModel -Models qwen3.5:4b -Repetitions 3 `
    -OllamaBaseUrl http://localhost:11435
~~~

If the requested weights are absent, the evaluation fails with the exact `ollama pull` commands; it never downloads
them automatically. Model promotion remains governed by M5.1 and requires zero security failures plus the documented
quality and hardware thresholds.

## Verified evidence

On 2026-09-05, the mandatory gate passed 133 backend tests and 60 frontend tests, lint, production build, both Compose
configurations, five-service health, a fresh coordinated backup, and isolated recovery. The recovery found six successful
Flyway migrations, booted Keycloak healthy with the `codex-of-realms` realm, validated the checksum-enabled manifest,
matched its zero source files, and removed only the temporary project volumes. The live-model gate had already passed
separately for `qwen3.5:4b` against the isolated Docker Ollama endpoint. The complete gate should be rerun after every
change to the persistence, identity, authorization, or web-client boundaries.
