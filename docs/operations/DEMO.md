# Campaign demo

This walkthrough uses El Meridiano de Ceniza to show how a Game Master and two players share campaign knowledge. Prepare the accounts and sources first, then follow the eight-minute presentation below.

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

3. Open [the application](http://localhost:5173). Register a local owner, open its verification message in [Mailpit](http://localhost:8025), follow the link and set the password when Keycloak asks. For an existing realm, first run `./scripts/configure-email-verification.ps1`; a startup import does not update it.
4. Create **El Meridiano de Ceniza**. Under **Añadir conocimiento**, upload the three files from `demo/lore/public/` with **Público** visibility and readable titles. Wait for publication. Read a source from the library to confirm availability.
5. Invite two local player addresses. Register each in a separate browser profile, verify its email and return to the app. Invitations activate on a matching verified login; creating an invitation does not send email.
6. Create a spoiler group, upload `demo/lore/spoilers/01-el-recuerdo-de-nara.md` under that group, and grant it to only the first player. Upload `demo/lore/gm-only/01-la-deuda-de-la-aguja.md` as GM-only. Select visibility in the form explicitly: Markdown front matter does not replace authorization.
7. In **Atlas del canon**, create **Lumbrevela** as a place and **La Aguja del Mediodía** as an object, attach visible source fragments, and relate them. Promote a reviewed proposal to canon. This is a manual editorial decision.

The campaign material is original to this project. Create the accounts through registration; the demo has no shared passwords.

Shortly before the session, ask “¿Dónde se alza Lumbrevela?” and wait for a cited answer. The first request may spend over a minute loading models; a healthy container does not mean the models are already loaded. If preparation fails, resolve it before inviting participants. Keep the session close to this check: the default model keep-alive is five minutes.

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
