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

## Limitations

- Ollama model weights and Prometheus/Grafana history are reproducible local dependencies and are not included.
- A backup is not verified until it has been restored successfully.
- The scripts target the local Docker Compose deployment, not a remote production installation.
