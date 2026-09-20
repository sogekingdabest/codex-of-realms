# Grounded answers

> Historical record. See [current documentation](../../README.md) for setup and behavior.

## Local models

M5 uses `bge-m3` for embeddings and `qwen3:4b` for Spanish answers. Download both before use:

~~~powershell
docker compose up -d postgres keycloak ollama
docker compose exec ollama ollama pull bge-m3
docker compose exec ollama ollama pull qwen3:4b
~~~

Compose enables both Ollama integrations. Outside Compose, set `AI_EMBEDDING_PROVIDER=ollama` and `AI_CHAT_PROVIDER=ollama` explicitly. Startup never downloads a model.

On a machine with NVIDIA Container Toolkit support, start Ollama with the explicit GPU override:

~~~powershell
docker compose -f compose.yaml -f compose.gpu.yaml up -d ollama
~~~

Set `AI_CHAT_MODEL` to use another installed chat model without rebuilding. Follow the
[local evaluation guide](../evaluation/LOCAL_MODEL_EVALUATION.md) before changing the default.

## API contract

Realm members ask a question with:

~~~http
POST /api/v1/realms/{realmId}/questions
Content-Type: application/json
Authorization: Bearer <token>

{"question":"¿En qué año apareció el Meridiano de Ceniza?"}
~~~

An accepted result has `outcome: ANSWERED`, citation markers in the answer, a citation array with immutable source coordinates, and embedding/chat provenance. A refusal has exactly `outcome: INSUFFICIENT_EVIDENCE`, `answer: null`, and an empty citation array. The refusal does not disclose whether relevant hidden material exists.

## Safety pipeline

1. Realm membership is required before embedding or retrieval.
2. PostgreSQL ranks only chunks visible to that member.
3. A deterministic gate checks injection indicators, similarity, and question/evidence term coverage.
4. The model receives only the bounded visible evidence as untrusted JSON, an exact provider-native JSON Schema,
   and no tools.
5. Every returned claim must cite a supplied rank and overlap its cited text.
6. Any invalid or unavailable model result fails closed to `INSUFFICIENT_EVIDENCE`.

The default calibration is intentionally conservative: 10 retrieved candidates, at most 6 model-context chunks, cosine similarity at least `0.45`, question coverage at least `0.70`, claim coverage at least `0.35`, an 8192-token context, and at most 768 predicted tokens. Thinking mode is disabled so the adapter receives only the requested JSON result. Override the `QA_*` or `AI_CHAT_*` environment variables only with recorded evaluation evidence.

## Diagnostics

Actuator exposes low-cardinality `codex.qa.duration` and `codex.qa.outcomes` meters. Raw questions, evidence, prompts, and answers are not metric labels and should not be logged.

If every question is refused, confirm both models are present with `docker compose exec ollama ollama list`, then inspect retrieval metrics before lowering thresholds. A missing or malformed chat response intentionally looks like insufficient evidence to the caller.
