# Campaign demo

This walkthrough uses El Meridiano de Ceniza to show how a Game Master and two players share campaign knowledge. Load the campaign first, then follow the eight-minute presentation below.

## Prepare once

Use Docker Desktop, a Node.js version supported by `frontend/package.json`, and JDK 21 for backend tests. Allow time for model downloads before presenting.

1. Copy `.env.example` to `.env` and choose local values for `POSTGRES_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD` and `GRAFANA_ADMIN_PASSWORD`.
2. From the repository root, run:

```powershell
docker compose up -d --build --wait
docker compose exec ollama ollama pull bge-m3
docker compose exec ollama ollama pull qwen3.5:4b
```

The downloads go into the stack's model volume, separate from a native Ollama installation. Use your `.env` tags if you changed the defaults, then reload the app.

3. Load the campaign. For a realm created before the Codex access theme, first run `./scripts/configure-keycloak-realm.ps1`; a startup import does not update an existing realm.

```powershell
./scripts/load-demo-campaign.ps1
```

The script creates three local accounts and prints one password for all of them:

| Account | Role | Sees |
|---|---|---|
| Inés Vidal, `ines@demo.invalid` | Game Master and owner | All seven sources, including two GM-only notes, and the whole atlas |
| Tala Mir, `tala@demo.invalid` | Player | Five sources: the three public ones and the two in the spoiler group **Recuerdos de Nara** |
| Oren Brea, `oren@demo.invalid` | Player | The three public sources and the public atlas |

As Inés, it creates **El Meridiano de Ceniza**, uploads the sources in `demo/lore` with their visibility, reveals the spoiler group to Tala and fills the atlas. Four atlas items stay as proposals, so promoting one to canon can be shown live. It ends by checking that each account sees exactly its share. The first upload waits for the embedding model, so a cold start can take a few minutes.

Running the script again sets a new password and keeps everything that already exists. It also completes a load that was interrupted. The campaign content is in `demo/campaign.json`. To sign in as several people at once, use a separate browser profile or private window for each account.

The script talks to Keycloak through a temporary client that it removes before finishing. Use it only on a local installation: the accounts use `.invalid` addresses and a password printed on screen.

### Prepare it by hand

To show registration and invitations as well, prepare the campaign from the application instead. Register an owner, open its verification message in [Mailpit](http://localhost:8025) and set the password when Keycloak asks. Create **El Meridiano de Ceniza** and upload the three files from `demo/lore/public/` as **Público**. Invite two player addresses and register each in a separate browser profile; invitations activate on a matching verified login and send no email. Create a spoiler group, upload `demo/lore/spoilers/01-el-recuerdo-de-nara.md` under it and grant it to one player. Upload `demo/lore/gm-only/01-la-deuda-de-la-aguja.md` as GM-only: Markdown front matter does not replace authorization. In **Atlas del canon**, create and relate **Lumbrevela** and **La Aguja del Mediodía** with visible source fragments, then promote a reviewed proposal to canon.

The campaign material is original to this project. No passwords are stored in the repository.

Shortly before the session, ask “¿Dónde se alza Lumbrevela?” and wait for a cited answer. The application starts loading both models when the backend starts and whenever someone opens **Consultas**. Ollama unloads them after five idle minutes (`AI_CHAT_KEEP_ALIVE` for the chat model). A cold load can take more than a minute, and a healthy container does not mean the models are loaded. If preparation fails, resolve it before inviting participants.

## Eight-minute presentation

| Time | Action | What the reviewer can assess |
|---|---|---|
| 0–1 min | Explain finding a campaign detail without leaking GM notes. | Who the product helps and why it exists. |
| 1–2 min | Search by title or filename, open a source, close with Escape. | Immediate usefulness without a model; keyboard access. |
| 2–3 min | Switch to a player, compare visible sources, reveal a spoiler to one player. | Server-controlled access with a meaningful workflow. |
| 3–4 min | Ask “¿Dónde se alza Lumbrevela?”, inspect an excerpt and its full document. | Traceability; assess the answer rather than promise one. |
| 4–5 min | Ask “¿Quién fundó la universidad de Aramonte?”, absent from the corpus. | Honest abstention and distinction from runtime failure. |
| 5–6 min | Follow an atlas relation to a specific entity and inspect its evidence. | Structured browsing and human control of canon. |
| 6–7 min | Replace a source and show progress/history while the previous version stays published. | Recoverable work and a usable intermediate state. |
| 7–8 min | Create a second universe, switch back, show architecture and measured checks. | Isolation and engineering choices tied to behavior. |

If inference fails, show the error and continue with the library and atlas. Explain that questions use uploaded documents, while atlas entries are edited separately. Keep the [current limitations](../product/LIMITATIONS.md) available for follow-up questions.

## Repeatable engineering checks

From `frontend`: `npm ci`, then `npm run verify`. From `backend`, with Docker running: `./mvnw.cmd --batch-mode --no-transfer-progress verify` (use `./mvnw` on Linux).

The [browser suite](../../OPERATIONS.md#reproducible-acceptance) runs these workflows with a model substitute: registration, invitations, source recovery, reading, citations, keyboard navigation and a second realm. `scripts/demo.ps1` checks the backend and Compose; run the frontend and browser commands separately.

To observe a group using the product without guidance, follow the [user session](../product/USER_VALIDATION.md).
