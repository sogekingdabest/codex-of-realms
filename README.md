# Codex of Realms

[![CI](https://github.com/sogekingdabest/codex-of-realms/actions/workflows/ci.yml/badge.svg)](https://github.com/sogekingdabest/codex-of-realms/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/sogekingdabest/codex-of-realms?include_prereleases&label=release)](https://github.com/sogekingdabest/codex-of-realms/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

**English** · [Español](README.es.md) · [Galego](README.gl.md)

A shared archive for tabletop role-playing campaigns. Keep session notes, characters and places together, and let each player see only what their character is allowed to know.

For example, the Game Master can keep the truth about an ancient tower private, share its public history with everyone, and reveal a clue to one player. Each person can browse their sources or ask a question and open the passages behind the answer.

The interface is in Spanish. Everything runs on your own machine, language models included. The first beta is [0.1.0-beta](CHANGELOG.md#010-beta--2-october-2026).

## What you can do

- Upload and read Markdown or text files, search by title or filename, and replace a document while its previous version stays available.
- Organize characters, places, factions, objects and events in the **Atlas del canon**, with relationships and source references.
- Invite editors and players, keep Game Master notes private and reveal spoilers to selected members.
- Ask questions about published sources and inspect the original text behind each citation.

The atlas is curated manually. Questions use uploaded documents; editing an atlas entry does not change those documents.

## See it in action

![Lumbrevela in the campaign atlas, with a relationship and its source alongside it](demo/assets/atlas.png)

[Watch the short walkthrough](demo/assets/recorrido.webm) · [Guided tour and screenshots](demo/RECORRIDO.md)

Captured on 20 September from an earlier version of the interface, with fictional campaign data and local models. The walkthrough is in Spanish and was recorded with the models already loaded.

## Run locally

You need:

- Docker with Compose.
- About 16 GB of RAM and room for the container images and about 5 GB of models. An NVIDIA GPU with 6 GB is recommended; without one, the models run on the CPU and answers take longer.
- PowerShell to load the demo campaign (`pwsh` on Linux and macOS).

1. Copy `.env.example` to `.env` and set the three passwords.
2. From the repository root, start the stack and download the models. The downloads are needed once per Ollama volume.

   ```sh
   docker compose up -d --build --wait
   docker compose exec ollama ollama pull bge-m3
   docker compose exec ollama ollama pull qwen3.5:4b
   ```

   With an NVIDIA GPU, start it with `docker compose -f compose.yaml -f compose.gpu.yaml up -d --build --wait` instead.
3. Load the demo campaign:

   ```sh
   ./scripts/load-demo-campaign.ps1
   ```

   It creates El Meridiano de Ceniza with three accounts and prints their password: Inés directs the game, Tala holds a spoiler and Oren sees only public canon.
4. Open [the application](http://localhost:5173) and sign in. Use a private window for each account to compare what they see.

To start your own campaign instead, register in the application and confirm your email in [Mailpit](http://localhost:8025), the local inbox. Then create a universe and upload your notes. If you opened the application before the models finished downloading, reload it.

The [demo guide](docs/operations/DEMO.md) has an eight-minute walkthrough. [Local development](docs/operations/LOCAL_DEVELOPMENT.md) covers ports and troubleshooting, and existing installations should follow the [upgrade steps](OPERATIONS.md#upgrade-an-existing-installation). Stop the stack with `docker compose down`; your data stays in the Docker volumes.

## How it works

React and TypeScript provide the client. A Java 21 Spring Boot application handles permissions, ingestion and questions. PostgreSQL stores campaign data and pgvector embeddings; Keycloak handles login, and Ollama runs the models.

The backend filters sources by permission before ranking search results. The model selects passages, and the server copies the original text into the answer. Document processing uses persistent jobs so failed work can be retried.

Read the [architecture](docs/architecture/ARCHITECTURE.md) for the reasoning behind these choices.

## Checks

With Java 21 and Docker available, run from `backend/`:

```sh
./mvnw --batch-mode --no-transfer-progress verify
```

On Windows, use `.\mvnw.cmd`. With Node.js 24, run from `frontend/`:

```sh
npm ci
npm run verify
```

These commands run the backend tests and frontend lint, tests, coverage checks and build. Browser tests, recovery drills and model evaluation have [separate instructions](OPERATIONS.md#reproducible-acceptance).

## Limitations

The supplied setup is for local use only. Uploads accept Markdown and text up to 1 MiB per file. A cited passage can be accurate and still incomplete, so the source reader stays available to check answers. The first answer after startup can take about a minute while the models load. See the [current limitations](docs/product/LIMITATIONS.md) for the full list.

[Documentation](docs/README.md) · [Roadmap](ROADMAP.md) · [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md)

## License

[MIT](LICENSE). The demonstration campaign is original project material. Dependencies retain their own licenses.
