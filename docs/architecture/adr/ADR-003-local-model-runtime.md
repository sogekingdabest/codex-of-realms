# ADR-003: Use server-side local models for the MVP

- **Status:** Accepted
- **Date:** 2026-08-26

## Context

The development machine has 16 GB RAM, an NVIDIA RTX 3060 Mobile with 6 GB VRAM and an AMD Ryzen 7 5800H. Candidate models must fit that memory budget and run with documented settings.

Browser inference through WebLLM or LiteRT-LM would introduce a different download, cache, memory and permission model. It needs its own evaluation.

## Decision

Use Ollama as the default server-side local runtime for the MVP. Depend on Spring AI's `ChatModel` and `EmbeddingModel` abstractions through application-owned ports.

Benchmark candidate small quantized chat models and multilingual embedding models on the target machine during M1/M4. Record model identifier, quantization, context size, latency, memory use, retrieval metrics, and quality results before selecting defaults.

CI must not download or execute a generative model. Deterministic model doubles test application behavior; separately labelled evaluation runs measure real models.

Defer browser inference and keep it out of the initial HTTP and application contracts.

## Consequences

### Positive

- Works without sending private campaign data to a hosted provider.
- Fits the available hardware and the local deployment.
- Preserves provider interchangeability at a meaningful boundary.
- Keeps CI stable and affordable.

### Negative

- Local generation can be slow and hardware-dependent.
- Portfolio reviewers may need to download a model separately.
- Chat and embedding model upgrades require evaluation; embedding changes require reindexing.

### Mitigation

- Provide a documented low-resource default and optional profiles later.
- Persist embedding provider, model, dimension, and configuration provenance.
- Version evaluation datasets and reports.
- Fail startup or ingestion clearly when configured model capabilities are incompatible.

## Deferred browser option

WebLLM or LiteRT-LM may be evaluated after a web client exists. Adoption requires a new ADR covering:

- Browser and device support
- Model download size and cache lifecycle
- Client-side authorization and protected-context exposure
- Whether retrieval remains server-side
- Quality parity and evaluation reproducibility
- Fallback behavior

## References

- [Spring AI model API](https://docs.spring.io/spring-ai/reference/api/)
- [Ollama documentation](https://docs.ollama.com/)
