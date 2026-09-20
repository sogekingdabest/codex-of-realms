# Operations and acceptance

Use this guide to upgrade an installation, run the checks and recover campaign data. For a first installation, start with the [README](README.md). The source and answer sections below describe the API behavior that clients need to handle.

## Upgrade an existing installation

1. Make a coordinated backup with the current database and original files.
2. Rebuild and deploy frontend and backend together: `docker compose up -d --build`.
3. Run `./scripts/configure-email-verification.ps1`. It updates only email verification and SMTP settings in the existing Keycloak realm. It does not recreate users or the realm.
4. Register a new account, open its verification email at http://localhost:8025 and follow the link. Keycloak requests the new password after email verification; complete this step before returning to the application. Demo accounts remain preverified.

Flyway V7 adds `email_verified=false` to existing application identities until their next synchronization. Existing memberships remain intact. New invitations stay pending until the recipient makes an authenticated request with the matching email and a boolean true verification claim; a previously stored value is insufficient. Owners share the application URL themselves because the backend sends no invitation notices.

V8 adds jobs and transition history. Existing unfinished versions become visible failed operations. Published versions remain available while a replacement is processed. A missing original can be uploaded over the same document from the processing panel.

## Source API

All routes use the prefix `/api/v1/realms/{realmId}`. Existing source reads expose only authorized READY versions. Job routes require an owner or editor.

| Request | Response |
|---|---|
| POST /sources, PUT /sources/{documentId} | Multipart with Idempotency-Key; 202 with {job, documentId, versionId} |
| POST /sources/{documentId}/reprocess | Idempotency-Key; same response; 200 when unchanged |
| GET /source-jobs | Administrative jobs and transition history |
| GET /source-jobs/{jobId} | One scoped job |
| POST /source-jobs/{jobId}/retry | Current job; duplicate retries while pending/running do not open another attempt budget |

An idempotency key contains 1–128 ASCII letters, digits, periods, underscores, colons or hyphens. Send the same key and payload to repeat an operation safely. Reusing the key with different content or parameters returns 409. If the first write is still in progress, a concurrent duplicate returns 425; retry with the same key.

The API returns 202 once it has saved the original file and a recoverable operation. Upload failures retain a failed operation and return an error that does not expose internal details.

The worker runs one job at a time by default (`SOURCE_WORKER_CONCURRENCY`). It claims work in a short transaction using `FOR UPDATE SKIP LOCKED`, then generates embeddings outside the transaction. A five-minute lease renews every thirty seconds. Progress updates and publication require its current, unexpired token.

After a crash, processing restarts from the original file rather than resuming a partial embedding batch.

Transient model/connection failures receive three attempts with 30-second and 2-minute delays. Missing files, incompatible processing configuration and permissions require intervention. Manual retry keeps the document/version, preserves history and opens a new three-attempt budget. A currently authorized editor can take responsibility. Reprocessing creates a version for the current configuration. Cancelled, deleted and superseded operations cannot publish.

The UI polls every two seconds while work is pending, cancels requests when the processing view unmounts, and refreshes published sources. It distinguishes a failed replacement from the still-published previous version.

## Extractive answer contract

The model receives at most six authorized passages from ten retrieved chunks. Candidate ordering uses question-term coverage, similarity, retrieval rank and offsets. Selection reserves the best passage per source, then fills the remaining places with passages that add coverage; ties keep their relevance order.

Paragraphs longer than 2,000 UTF-16 characters are split into sentence windows, with one sentence of overlap where possible. A single sentence over that limit is excluded. Upload and source-content responses report `excludedSentences`, which the UI shows as a warning. Text and offsets remain unchanged.

Source reads are reused by version within a request, but final permission and active-version checks run again. The selector returns only `outcome` and `passageIds`. Unknown or duplicate IDs, malformed JSON, inconsistent outcomes and generated text fields fail validation. Relevance checks use the selected passages; evaluation fact scoring is frozen separately.

Responses retain `ANSWERED`/`INSUFFICIENT_EVIDENCE`, answer, citations and provenance, with `answerMode=EXTRACTIVE` and `excerpts=[{text,citationRank}]`. The server copies up to three excerpts and 6,000 content characters from the originals. If a passage becomes unavailable before the final checks, the whole result is rejected without revealing the access change. `MODEL_UNAVAILABLE`, `VALIDATION_FAILED` and `NO_EVIDENCE` remain distinct reasons.

The browser renders passages as plain text. Before paragraph expansion, lexical overlap filters the retrieved context, including headings. Full candidate paragraphs are then screened for instruction patterns. See [answer limitations](docs/product/LIMITATIONS.md#sources-and-questions) when interpreting the result.

Nginx accepts 2 MiB multipart requests, while the backend file limit is 1 MiB. The proxy waits 180 seconds for API responses; the model timeout is 120 seconds.

## Reproducible acceptance

Run `./mvnw.cmd verify` from `backend` with Docker running for PostgreSQL concurrency, authorization and migration tests. From `frontend`, run `npm ci` and `npm run verify` for lint, tests, coverage thresholds, a non-empty LCOV report and the production build. CI and `scripts/verify-m8.2.ps1` use the same frontend command. `npm test` is useful during development but omits the full verification steps.

Then run the browser and recovery checks below. Frontend verification alone does not require Docker.

The browser suite uses a temporary independent stack, ports and volumes with a deterministic Ollama-compatible test server. Install Chromium once from `frontend` with `npx playwright install chromium`. Run the following from the repository root; the cleanup keeps only the main application project after the check, including when a test fails:

~~~powershell
try {
    docker compose -f compose.e2e.yaml up -d --build --wait
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the isolated browser environment.' }
    npm --prefix frontend run test:e2e
    if ($LASTEXITCODE -ne 0) { throw 'Browser acceptance failed; inspect frontend/playwright-report.' }
} finally {
    docker compose -f compose.e2e.yaml down --volumes --remove-orphans
}
~~~

The tests register local users and verify mail through Keycloak/Mailpit. A focused reader scenario uploads an original demo document and checks the modal keyboard loop, Escape, focus restoration and mobile layout. The owner-and-two-player scenario accepts consecutive invitations, uploads a source, observes transient failure/retry, keeps a published version during a failed replacement, retries manually, searches and reads sources, queries and opens literal evidence, then creates a second isolated universe and returns to the first. Successful screenshots are stored in `frontend/test-results/`; failed runs retain traces. The browser job in CI runs the same isolated stack and suite. Never use its fixed test credentials outside this isolated stack. The test server supplies deterministic model responses.

Use the [model-evaluation guide](demo/evaluation/README.md) for live inference. Report version 3 records literal copying, selection relevance, rejection and latency against oracle-visible evidence. Vector retrieval and authorization are checked separately by integration and browser tests.

The measured comparison of eight installed models is preserved in [the acceptance report](reviews/2026-09-06-entrega-y-validacion.md) and [its machine-readable results](reviews/2026-09-06-selector-results.json). The historical quality thresholds were not met by any model; no model is promoted automatically.

## Backup and recovery

Run `./scripts/backup.ps1`. PostgreSQL must be running and the application container must exist. The script stops the backend for the coordinated database/file copy and restores its prior running/stopped state in a finally block, including on error. The manifest includes file checksums and pending job identities/states.

Run `./scripts/verify-restore.ps1 -BackupDirectory <directory>` to restore into separate volumes, check file hashes and pending jobs, and boot Keycloak against the preserved schema. Add `-ResumePendingJobs` to start the restored backend and require every pending operation to complete and publish within ten minutes. Its configured Ollama endpoint must be reachable and use the same processing model. Pending QUEUED/RUNNING jobs must have intact originals; interrupted UPLOADING jobs are reconciled by the worker. Leases from a backup expire normally after restoration. The worker requires compatible processing configuration and current requester permission to publish.

Both backup and restore support databases predating V8. `backup.ps1 -ProjectName codex-of-realms-e2e` targets only the isolated acceptance project. The no-Docker script `./scripts/tests/backup-contract.ps1` checks backend state restoration on success, an initially stopped backend, copy failure and the legacy schema. It complements the real restore test.

Source jobs expose `codex.source.jobs.pending`, `codex.source.jobs.duration`, `codex.source.jobs.results` and `codex.source.jobs.retries`, alongside QA duration/outcome metrics. Labels contain only bounded outcomes and reasons. See [observability](docs/operations/OBSERVABILITY.md) for the local dashboard.
