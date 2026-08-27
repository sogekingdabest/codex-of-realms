# ADR-008: Select the local chat model through a reproducible safety-first evaluation

- **Status:** Accepted
- **Date:** 2026-08-27

## Context

ADR-007 chose `qwen3:4b` as a conservative first baseline for a machine with 16 GB of RAM and an RTX 3060 Mobile
with 6 GB of VRAM. That choice was not a claim that it remained the best compact model. Model families, quantized
builds, runtimes, and structured-output support change faster than the application architecture, while an
unrecorded recommendation cannot justify changing a security-sensitive default.

The relevant question is also broader than prose quality. Codex of Realms needs Spanish instruction following,
exact structured output, correct visible citations, refusal under insufficient evidence, no restricted-fact
leakage, and acceptable latency on the target hardware.

## Decision

- Keep `qwen3:4b` as the incumbent until a recorded target-machine evaluation identifies an eligible replacement.
- Compare explicit Ollama tags, initially `qwen3:4b`, `qwen3.5:4b`, `gemma4:e2b-it-qat`, and `phi4-mini:3.8b`.
- Never download model weights from application startup or the evaluation script. A developer must inspect and
  pull every candidate explicitly.
- Run generation evaluation against the versioned Spanish baseline with oracle, actor-visible evidence. This
  isolates answer generation; the existing M4 suite remains responsible for retrieval and authorization.
- Require zero restricted-fact leaks and zero unexpected answers in negative cases. Quality thresholds cannot
  compensate for a security failure.
- Record outcome accuracy, expected-fact lexical coverage, structured-output validity, citation correctness,
  latency, generation throughput, model tag, Git commit, runtime process data, and hardware.
- Require provider-native JSON Schema on every model request and retain deterministic post-generation validation.
- Keep the provider-neutral `GroundedAnswerModel` boundary so a later Ollama alternative or browser runtime can be
  evaluated without weakening the application contract.

## Consequences

Model selection becomes evidence-based and repeatable, but no single benchmark proves semantic correctness. The
lexical fact metric is a regression signal, and a human should inspect outputs before changing the default. Reports
are local artifacts by default because timings and model output depend on hardware and may reveal environment data.

The generation harness deliberately does not measure `bge-m3` recall or the authenticated HTTP authorization
boundary. Those properties remain covered by the M4 retrieval evaluation and deterministic integration suite.

## Rejected alternatives

- Switch to the newest model based only on release recency or aggregate public benchmarks.
- Select the fastest model without making security failures a blocker.
- Mix retrieval and generation into one score, which would obscure the component responsible for a regression.
- Auto-pull all candidates, because model downloads are large, mutable external actions.
