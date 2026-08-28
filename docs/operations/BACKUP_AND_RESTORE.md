# Backup and restore

Codex of Realms persists application data and Keycloak in PostgreSQL and keeps immutable uploaded source files in the
`source-data` volume. A valid backup must contain both parts from the same running stack.

## Create a backup

Start the normal stack, then run from the repository root:

~~~powershell
.\scripts\backup.ps1
~~~

The command creates an ignored timestamped directory under `backups/` containing:

- `codex-of-realms.dump`: a PostgreSQL custom archive with the application and `keycloak` schemas.
- `sources/`: the raw immutable source tree.
- `manifest.json`: creation time and Git revision.

New M8.2 manifests also record the database SHA-256 and each source file's relative path, size, and SHA-256 so corruption
or an incomplete source copy is detected during verification.

Use `-DestinationRoot D:\safe\codex-backups` to store the result outside the repository. Copy completed backups to a
different disk before treating them as durable.

## Restore a backup

Restoration replaces the current PostgreSQL contents and source volume. Stop active use, verify the selected directory,
and run the explicit confirmation form:

~~~powershell
.\scripts\restore.ps1 -BackupDirectory D:\safe\codex-backups\20260828-180000 -ConfirmRestore
~~~

The script stops application database clients, restores PostgreSQL and sources, and restarts the stack. Afterward,
verify login, realm membership, one source, and one citation. Keep the original backup until that smoke succeeds.

## Verify without touching the active stack

Use the isolated verifier before depending on a backup:

~~~powershell
.\scripts\verify-restore.ps1 -BackupDirectory .\backups\20260828-180000
~~~

It restores into a uniquely named Compose project with fresh PostgreSQL and source volumes, no published ports, and a
Keycloak instance used only for its health check. It verifies migration history, source-file parity, and the imported
realm, then removes only those temporary containers and volumes. Add `-KeepEnvironment` solely when diagnosing a failed
check; the command prints the exact isolated project name to clean up afterward. Source hashes are recomputed inside the
temporary volume, so the drill validates content rather than only counting copied files.

## Limitations

- Ollama model weights and Prometheus/Grafana history are reproducible local dependencies and are not included.
- A backup is not verified until `verify-restore.ps1` or an equivalent recovery drill succeeds.
- The scripts target the local Docker Compose deployment, not a remote production installation.
