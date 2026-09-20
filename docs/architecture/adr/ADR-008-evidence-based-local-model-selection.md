# ADR-008: Select the local chat model through a reproducible safety-first evaluation

- **Status:** Accepted
- **Date:** 2026-09-05

## Context

ADR-007 started with `qwen3:4b` on the 16 GB RAM, 6 GB VRAM development machine. Replacing it requires a comparison on the same hardware and task, including its ability to return the required structure and citations.

The comparison measures Spanish instruction following, structured output, visible citations, refusal, restricted-fact leakage and latency.

## Decision

- Promote `qwen3.5:4b` as the default local chat model after the recorded target-machine evaluation identified it
  as the highest-quality eligible replacement. Its selected digest is
  `2a654d98e6fba55d452b7043684e9b57a947e393bbffa62485a7aac05ee4eefd` (Q4_K_M).
- Compare explicit Ollama tags in two stages. The September 2026 screening set is `qwen3:4b`, `qwen3.5:4b`,
  `gemma4:e2b-it-qat`, `granite4.2:3b-q4_K_M`, `ministral-3:3b-instruct-2512-q4_K_M`,
  `nemotron-3-nano:4b`, `phi4-mini:3.8b-q4_K_M`, and `LiquidAI/lfm2.5-1.2b-instruct:q4_k_m`.
- Never download model weights from application startup or the evaluation script. A developer must inspect and
  pull every candidate explicitly.
- Run generation evaluation against the versioned Spanish baseline with oracle, actor-visible evidence. This
  isolates answer generation; the existing M4 suite remains responsible for retrieval and authorization.
- Require zero restricted-fact leaks and zero unexpected answers in negative cases. Quality thresholds cannot
  compensate for a security failure.
- Record outcome accuracy, expected-fact lexical coverage, structured-output validity, citation correctness,
  cold-start and warm latency, generation throughput, model tag and digest, Git commit, runtime process data,
  and hardware. Screening uses one run; the incumbent and up to three eligible challengers advance to three runs.
- Require provider-native JSON Schema on every model request and retain deterministic post-generation validation.
- Keep the provider-neutral `GroundedAnswerModel` boundary so a later Ollama alternative or browser runtime can be
  evaluated without weakening the application contract.

## Recorded decision evidence

On the target RTX 3060 Laptop GPU, all eight candidates completed the one-run screening without technical or
security failures. `qwen3:4b`, `qwen3.5:4b`, `granite4.2:3b-q4_K_M`, and the safe near-miss
`gemma4:e2b-it-qat` advanced to three repetitions and a blinded review of 252 non-delegated results.

Only Qwen 3.5 and Granite remained automatically eligible. Both achieved 1.000 outcome accuracy, 1.000 structured
output and citations, and zero security failures. Qwen 3.5 achieved 0.815 fact coverage and 4.91/5 reviewed Spanish
clarity; Granite achieved 0.704 and 4.75/5. Granite's 3,698 ms warm p95 was lower than Qwen's 6,066 ms, but its
0.111 fact-coverage deficit exceeded the 0.03 quality-equivalence band, so latency could not override quality.

The selected Qwen artifact repeated successfully in Ollama 0.33.2 Docker with 0.889 fact coverage, zero security
failures, and full GPU residency. It also coexisted fully in VRAM with `bge-m3` under
`OLLAMA_MAX_LOADED_MODELS=2`, without OOM, timeouts, or sustained CPU fallback. The evaluation used commit
`b9a7c1e2362f9572c4905bd4f749953a0478ac1a`.

## Consequences

The lexical fact metric helps detect regressions; outputs also need human review before changing the default. Raw reports stay local because they include model output and machine details.

The generation harness deliberately does not measure `bge-m3` recall or the authenticated HTTP authorization
boundary. Those properties remain covered by the M4 retrieval evaluation and deterministic integration suite.

## Rejected alternatives

- Switch to the newest model based only on release recency or aggregate public benchmarks.
- Select the fastest model without making security failures a blocker.
- Mix retrieval and generation into one score, which would obscure the component responsible for a regression.
- Auto-pull all candidates, because model downloads are large, mutable external actions.
