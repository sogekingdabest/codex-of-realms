# M4 retrieval baseline

M4 and M7 evaluate source-level retrieval against [`demo/evaluation/baseline.json`](../../demo/evaluation/baseline.json). The dataset remains the semantic source of truth; the test does not duplicate its answerable questions or expected source identifiers. Version 2 expands the baseline to 22 cases over seven original Spanish sources and adds broader public, GM-only, spoiler, restricted, and prompt-injection coverage.

## Acceptance targets

- Unauthorized evidence rate: exactly `0%`.
- Access precision: exactly `100%`.
- Source recall@10 across `ANSWERED` cases: at least `0.90`.
- Outsider query: non-disclosing HTTP 404 before query embedding.
- Ranking: exact cosine distance, deterministic chunk-ID tie break.

The container-backed integration case creates the original Spanish realm, ingests all four Markdown sources, establishes owner/player/spoiler perspectives, and runs the versioned questions through the HTTP API. CI uses a deterministic lexical embedding double so security and orchestration remain reproducible without downloading a model. A real `bge-m3` run is a separate hardware/model evaluation and must record the repository commit and model tag.

## Retrieval API

`POST /api/v1/realms/{realmId}/retrieval`

~~~json
{
  "question": "¿Qué ocurrió durante la Noche del Cielo Partido?",
  "limit": 10
}
~~~

Each evidence item contains rank, cosine distance and similarity, chunk text, structural heading and offsets, immutable source/version/chunk identifiers, source title and checksum, and the effective policy classification. M5 will consume this contract behind the `LoreRetriever` port rather than trusting client-provided evidence.

## Metrics

- `codex.retrieval.duration{outcome=success|failure}`
- `codex.retrieval.results`
- `codex.retrieval.distance`

Metrics deliberately omit questions, users, realms, source names, and lore text.
