# ADR-003: Use server-side local models for the MVP

- **Status:** Accepted
- **Date:** 2026-08-26

## Context

The target development machine has 16 GB RAM, an NVIDIA RTX 3060 Mobile with 6 GB VRAM, and an AMD Ryzen 7 5800H. It can run small quantized models, but model choice must respect limited memory and should remain reproducible for portfolio reviewers.

The product may later benefit from browser-side inference through WebLLM or LiteRT-LM, but browser execution changes model distribution, memory constraints, caching, privacy, compatibility, and authorization assumptions.

## Decision

Use Ollama as the default server-side local runtime for the MVP. Depend on Spring AI's `ChatModel` and `EmbeddingModel` abstractions through application-owned ports.

Benchmark candidate small quantized chat models and multilingual embedding models on the target machine during M1/M4. Record model identifier, quantization, context size, latency, memory use, retrieval metrics, and quality results before selecting defaults.

CI must not download or execute a generative model. Deterministic model doubles test application behavior; separately labelled evaluation runs measure real models.

Browser-side inference is deferred. The HTTP and application contracts must not assume that it exists, and no browser-model abstraction will be introduced speculatively.

## Consequences

### Positive

- Works without sending private campaign data to a hosted provider.
- Matches the available hardware and local-first product story.
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
