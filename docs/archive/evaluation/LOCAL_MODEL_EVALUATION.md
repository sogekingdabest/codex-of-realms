# Local chat model evaluation

> Historical record. See [current documentation](../../README.md) for setup and behavior.

## Purpose and boundary

M5.1 compares chat models on the target laptop. Answerable cases receive their expected actor-visible sources;
negative cases receive all actor-visible demo sources. This isolates generation, structured output, citations,
and refusal behaviour from retrieval.

The `authorization-001` case is delegated to the deterministic API and M4 suites, which test membership and
hidden-source filtering. The model comparison does not test those boundaries.

## Candidate set

The September 2026 comparison keeps the incumbent, the previous compact candidates, and four newer edge models.
All tags are capped below 4.5 GB so the 8,192-token application context still has headroom on the target RTX 3060
Laptop GPU with 6 GB VRAM:

| Ollama tag | Role in the comparison |
|---|---|
| [`qwen3:4b`](https://ollama.com/library/qwen3:4b) | Incumbent selected in ADR-007 |
| [`qwen3.5:4b`](https://ollama.com/library/qwen3.5:4b) | Newer compact Qwen candidate |
| [`gemma4:e2b-it-qat`](https://ollama.com/library/gemma4:e2b-it-qat) | Compact Gemma 4 instruction candidate |
| [`granite4.2:3b-q4_K_M`](https://ollama.com/library/granite4.2:3b-q4_K_M) | RAG- and structured-JSON-oriented compact candidate |
| [`ministral-3:3b-instruct-2512-q4_K_M`](https://ollama.com/library/ministral-3) | Multilingual edge instruction candidate |
| [`nemotron-3-nano:4b`](https://ollama.com/library/nemotron-3-nano:4b) | Compact reasoning/non-reasoning candidate |
| [`phi4-mini:3.8b-q4_K_M`](https://ollama.com/library/phi4-mini:3.8b-q4_K_M) | Compact alternative family |
| [`LiquidAI/lfm2.5-1.2b-instruct:q4_k_m`](https://ollama.com/LiquidAI/lfm2.5-1.2b-instruct) | Sub-1 GB latency floor |

Tags are inputs, not endorsements. Confirm their license, size, quantization, and availability in your installed
Ollama version before downloading them.

## Prepare native Ollama

The target workstation runs native Ollama on `http://localhost:11434`. Pull each inspected tag explicitly; the
evaluation script never downloads weights:

~~~powershell
$ollama = "C:\Users\Dani\AppData\Local\Programs\Ollama\ollama.exe"
& $ollama pull qwen3:4b
& $ollama pull qwen3.5:4b
& $ollama pull gemma4:e2b-it-qat
& $ollama pull granite4.2:3b-q4_K_M
& $ollama pull ministral-3:3b-instruct-2512-q4_K_M
& $ollama pull nemotron-3-nano:4b
& $ollama pull phi4-mini:3.8b-q4_K_M
& $ollama pull LiquidAI/lfm2.5-1.2b-instruct:q4_k_m
& $ollama list
~~~

The script unloads resident models between candidates through Ollama's API so cold-start and warm latency are
comparable. It records the resolved name, digest, size, parameter count, quantization, Ollama version, and active
GPU process in report schema version 2.

## Prepare Docker Ollama and the GPU

With Docker Desktop and NVIDIA container support available:

~~~powershell
docker compose -f compose.yaml -f compose.gpu.yaml -f compose.evaluation.yaml up -d ollama
docker compose exec ollama ollama pull qwen3.5:4b
docker compose exec ollama ollama list
~~~

`compose.evaluation.yaml` replaces the normal port mapping with loopback-only `127.0.0.1:11435`, so native Ollama
can remain on `11434`. Pull only the selected winner into Docker before the final validation.

The base Compose file remains CPU-compatible. `compose.gpu.yaml` is the explicit NVIDIA GPU override. Ollama may
keep two models resident so `bge-m3` and the chat model can coexist; set `OLLAMA_MAX_LOADED_MODELS=1` if 6 GB of
VRAM causes pressure, accepting additional model swaps.

## Run the two-stage comparison

From the repository root:

~~~powershell
.\scripts\evaluate-local-models.ps1 -Stage Screening
~~~

The screening stage defaults to all eight candidates and one repetition. Its comparison JSON and Markdown include
the incumbent plus up to three proposed challengers. Pass those exact tags to the final stage, which defaults to
three repetitions and also creates a blinded CSV review template plus a separate alias key:

~~~powershell
$finalists = @("qwen3:4b", "candidate-1", "candidate-2", "candidate-3")
.\scripts\evaluate-local-models.ps1 -Stage Final -Models $finalists
~~~

To use another server:

~~~powershell
.\scripts\evaluate-local-models.ps1 -Stage Final -Models qwen3:4b `
    -OllamaBaseUrl http://localhost:11435
~~~

Each model creates JSON and Markdown reports under `demo/evaluation/results/`, followed by JSON and Markdown
comparison artifacts. These remain ignored because raw answers and machine details are local evidence.
The Maven profile can also be called directly from `backend` after setting `AI_CHAT_MODEL`:

~~~powershell
$env:AI_CHAT_MODEL = "qwen3.5:4b"
.\mvnw.cmd --batch-mode --no-transfer-progress -Plocal-model-evaluation verify
~~~

Normal `test` and `verify` runs do not invoke a real model.
The evaluation HTTP read timeout defaults to five minutes and can be changed with
`LOCAL_MODEL_HTTP_READ_TIMEOUT_SECONDS`; the application default is two minutes through `AI_HTTP_READ_TIMEOUT`.

## Reviewed target-hardware result

The full comparison completed on 2026-09-05 at commit `b9a7c1e2362f9572c4905bd4f749953a0478ac1a`, with
native and Docker Ollama 0.33.2, Windows 11, Ryzen 7 5800H, 16 GB RAM, and an RTX 3060 Laptop GPU with
6,144 MiB VRAM. All eight models completed screening without technical failures or security failures.

| Model | Outcome | Facts | JSON | Citations | Warm p95 | Screening result |
|---|---:|---:|---:|---:|---:|---|
| `qwen3:4b` | 0.952 | 0.778 | 1.000 | 0.923 | 4,981 ms | Incumbent finalist; below citation threshold |
| `qwen3.5:4b` | 1.000 | 0.815 | 1.000 | 1.000 | 4,854 ms | Finalist |
| `gemma4:e2b-it-qat` | 0.952 | 0.889 | 1.000 | 0.923 | 4,648 ms | Safe near-miss finalist |
| `granite4.2:3b-q4_K_M` | 1.000 | 0.704 | 1.000 | 1.000 | 3,270 ms | Finalist |
| `ministral-3:3b-instruct-2512-q4_K_M` | 0.952 | 0.778 | 1.000 | 0.923 | 3,351 ms | Below citation threshold |
| `nemotron-3-nano:4b` | 1.000 | 0.630 | 1.000 | 1.000 | 4,247 ms | Below fact threshold |
| `phi4-mini:3.8b-q4_K_M` | 0.381 | 0.000 | 1.000 | 0.000 | 4,953 ms | Ineligible |
| `LiquidAI/lfm2.5-1.2b-instruct:q4_k_m` | 0.381 | 0.000 | 1.000 | 0.000 | 1,584 ms | Ineligible |

The four finalists then ran three repetitions. The blinded review covered all 252 non-delegated results.

| Model | Eligible | Outcome | Facts | Citations | Warm p95 | Human clarity | Human support/abstention |
|---|---:|---:|---:|---:|---:|---:|---:|
| `qwen3.5:4b` | yes | 1.000 | 0.815 | 1.000 | 6,066 ms | 4.91/5 | 100% / 100% |
| `granite4.2:3b-q4_K_M` | yes | 1.000 | 0.704 | 1.000 | 3,698 ms | 4.75/5 | 100% / 100% |
| `gemma4:e2b-it-qat` | no | 0.952 | 0.889 | 0.923 | 2,901 ms | 4.76/5 | 100% / 95.2% |
| `qwen3:4b` | no | 0.952 | 0.778 | 0.923 | 5,070 ms | 4.48/5 | 95.2% / 95.2% |

`qwen3.5:4b` wins because Granite's fact coverage is 0.111 lower, outside the 0.03 quality-equivalence band;
the lower latency therefore cannot decide the comparison. The selected artifact is digest
`2a654d98e6fba55d452b7043684e9b57a947e393bbffa62485a7aac05ee4eefd`, Q4_K_M.

Docker repeated the winner for three runs against `127.0.0.1:11435`: outcome 1.000, fact coverage 0.889,
JSON and citations 1.000, zero security failures, warm median 3,707 ms, and warm p95 7,506 ms. `qwen3.5:4b`
and `bge-m3` then remained simultaneously and fully resident in GPU (`size_vram == size`) with no OOM,
timeout, or sustained CPU fallback.

## Earlier target-hardware smoke

The first development smoke was recorded on 2026-08-27 against commit `709495c` with native Ollama 0.33.1,
an RTX 3060 Laptop GPU with 6 GB VRAM, 16 GB system RAM, and a Ryzen 7 5800H. The evaluated model was
`qwen3.5:4b`, digest `2a654d98e6fb`, Q4_K_M, with an 8192-token context and full model residency in VRAM.

| Runs | Outcome | Fact coverage | JSON | Citations | Security failures | Median | p95 | Generation |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1.000 | 0.789 | 1.000 | 1.000 | 0 | 2956 ms | 13992 ms | 67.42 tok/s |

The result was eligible under the automated thresholds but was only a smoke. It is retained as historical context;
the reviewed comparison above supersedes it.

## Interpretation and promotion rule

The report marks a model eligible only when all of these hold:

- no negative case unexpectedly returns an answer;
- no forbidden fact crosses the configured lexical leak threshold;
- outcome accuracy is at least `0.80`;
- expected-fact coverage is at least `0.70`;
- valid structured output and citation success are each at least `0.95`.

Security failures are hard blockers. Among eligible candidates, review every answer manually, then prefer the best
quality/latency trade-off that fits without sustained VRAM spill. The recorded comparison promoted `qwen3.5:4b`;
future replacements must repeat this process before changing the default again.

Metrics are deliberately modest: expected-fact and forbidden-fact checks use significant-term lexical coverage,
not an LLM judge. They are useful for reproducible regression detection but do not establish entailment on their
own.
