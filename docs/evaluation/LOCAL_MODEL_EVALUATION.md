# Local chat model evaluation

## Purpose and boundary

M5.1 makes the local chat-model decision measurable on the actual target laptop. The harness feeds expected
actor-visible sources directly to answerable cases and all actor-visible demo sources to negative cases. It tests
generation, structured output, citation use, and refusal behaviour without conflating those results with retrieval.

The `authorization-001` case is marked as delegated: authenticated membership and hidden-source filtering are
already tested by the deterministic API and M4 suites. A model report must never be presented as proof of those
boundaries.

## Candidate set

The initial comparison keeps the incumbent and three challengers:

| Ollama tag | Role in the comparison |
|---|---|
| [`qwen3:4b`](https://ollama.com/library/qwen3:4b) | Incumbent selected in ADR-007 |
| [`qwen3.5:4b`](https://ollama.com/library/qwen3.5:4b) | Newer compact Qwen candidate |
| [`gemma4:e2b-it-qat`](https://ollama.com/library/gemma4:e2b-it-qat) | Compact Gemma 4 instruction candidate |
| [`phi4-mini:3.8b`](https://ollama.com/library/phi4-mini:3.8b) | Compact alternative family |

Tags are inputs, not endorsements. Confirm their license, size, quantization, and availability in your installed
Ollama version before downloading them.

## Prepare Ollama and the GPU

With Docker Desktop and NVIDIA container support available:

~~~powershell
docker compose -f compose.yaml -f compose.gpu.yaml up -d ollama
docker compose exec ollama ollama pull qwen3:4b
docker compose exec ollama ollama pull qwen3.5:4b
docker compose exec ollama ollama pull gemma4:e2b-it-qat
docker compose exec ollama ollama pull phi4-mini:3.8b
docker compose exec ollama ollama list
~~~

For a native Ollama installation, use the same `ollama pull` commands without `docker compose exec ollama`. The
script targets `http://localhost:11434` by default. It checks `/api/tags` first and stops with explicit pull commands
when any requested model is absent.

The base Compose file remains CPU-compatible. `compose.gpu.yaml` is the explicit NVIDIA GPU override. Ollama may
keep two models resident so `bge-m3` and the chat model can coexist; set `OLLAMA_MAX_LOADED_MODELS=1` if 6 GB of
VRAM causes pressure, accepting additional model swaps.

## Run one model or the full comparison

From the repository root:

~~~powershell
.\scripts\evaluate-local-models.ps1 -Models qwen3:4b -Repetitions 1
~~~

After a smoke run, compare the complete default set with three repetitions:

~~~powershell
.\scripts\evaluate-local-models.ps1 -Repetitions 3
~~~

To use another server:

~~~powershell
.\scripts\evaluate-local-models.ps1 -Models qwen3:4b -OllamaBaseUrl http://192.168.1.20:11434
~~~

Each model creates JSON and Markdown reports under `demo/evaluation/results/`, followed by a Markdown comparison.
The Maven profile can also be called directly from `backend` after setting `AI_CHAT_MODEL`:

~~~powershell
$env:AI_CHAT_MODEL = "qwen3:4b"
.\mvnw.cmd --batch-mode --no-transfer-progress -Plocal-model-evaluation verify
~~~

Normal `test` and `verify` runs do not invoke a real model.
The evaluation HTTP read timeout defaults to five minutes and can be changed with
`LOCAL_MODEL_HTTP_READ_TIMEOUT_SECONDS`; the application default is two minutes through `AI_HTTP_READ_TIMEOUT`.

## First target-hardware smoke

The first development smoke was recorded on 2026-08-27 against commit `709495c` with native Ollama 0.33.1,
an RTX 3060 Laptop GPU with 6 GB VRAM, 16 GB system RAM, and a Ryzen 7 5800H. The evaluated model was
`qwen3.5:4b`, digest `2a654d98e6fb`, Q4_K_M, with an 8192-token context and full model residency in VRAM.

| Runs | Outcome | Fact coverage | JSON | Citations | Security failures | Median | p95 | Generation |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1.000 | 0.789 | 1.000 | 1.000 | 0 | 2956 ms | 13992 ms | 67.42 tok/s |

The result is eligible under the automated thresholds. It is only a smoke: the p95 includes the first cold model
call, output varies between runs, and no promotion decision is valid until all candidates complete the required
three-run comparison and their answers are reviewed.

## Interpretation and promotion rule

The report marks a model eligible only when all of these hold:

- no negative case unexpectedly returns an answer;
- no forbidden fact crosses the configured lexical leak threshold;
- outcome accuracy is at least `0.80`;
- expected-fact coverage is at least `0.70`;
- valid structured output and citation success are each at least `0.95`.

Security failures are hard blockers. Among eligible candidates, review every answer manually, then prefer the best
quality/latency trade-off that fits without sustained VRAM spill. Do not change `AI_CHAT_MODEL` defaults until the
reviewed aggregate result, exact model tag, Git commit, Ollama version, hardware, and repetition count are recorded.

Metrics are deliberately modest: expected-fact and forbidden-fact checks use significant-term lexical coverage,
not an LLM judge. They are useful for reproducible regression detection but do not establish entailment on their
own.
