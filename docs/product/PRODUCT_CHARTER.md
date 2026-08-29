# Product Charter

- **Product:** Codex of Realms
- **Status:** M9 evidence-backed canon workspace
- **Date:** 2026-08-29

## Product definition

Codex of Realms is an evidence-first lore assistant for fictional worlds and tabletop role-playing campaigns. It stores curated source material, retrieves only the evidence an authenticated viewer may access, and uses that evidence to answer natural-language questions with citations or an explicit refusal when the evidence is insufficient.

## Problem statement

Long-running fictional worlds accumulate fragmented notes, session records, character descriptions, and private Game Master material. Searching this corpus manually is slow, and generic document chat systems do not provide a strong boundary between public knowledge, spoilers, private material, canon, and model-generated suggestions.

## Core differentiator

**Access-aware, evidence-first RAG for fictional canon.**

The differentiator is not prose generation. It is the combination of:

1. Provenance-backed factual answers.
2. Authorization and spoiler filtering before retrieval.
3. A first-class insufficient-evidence outcome.
4. Explicit human control over what becomes canon.

## Primary users

### Realm owner / Game Master

Creates a realm, curates its source material, manages members and access, and controls the canonical state of the world.

### Collaborator / Writer

Maintains sources, entities, and relations within the permissions granted by the owner.

### Player / Reader

Explores the world through questions while seeing only public and explicitly revealed knowledge.

## Jobs to be done

- Find when and where a canonical fact was introduced.
- Ask what a specific viewer is allowed to know about an event.
- Trace relationships between important lore entities.
- Receive a useful refusal instead of a fabricated answer.
- Inspect the evidence behind an answer.
- Experiment with creative suggestions without silently modifying canon.

## Product principles

### Evidence before eloquence

A short cited answer is better than a fluent unsupported answer.

### Authorization before retrieval

Protected chunks must never enter the candidate context for an unauthorized viewer. Prompt instructions are not an authorization mechanism.

### Human-owned canon

Model output is transient or `PROPOSED` until a permitted human explicitly promotes it.

### Observable uncertainty

The API exposes insufficient evidence and retrieval diagnostics in a safe form instead of hiding uncertainty behind generic prose.

### Small, reversible architecture

The product begins as a modular monolith. New processes, brokers, databases, and model runtimes require measured need.

## MVP product outcomes

The MVP is successful when a reviewer can:

1. Start the complete local environment from documented commands.
2. Authenticate as a Game Master and as two players with different knowledge.
3. Upload an original Spanish lore document and observe its processing state.
4. Ask an answerable question and inspect valid citations.
5. Ask an unsupported question and receive `INSUFFICIENT_EVIDENCE`.
6. Ask the same spoiler question as two players and receive different, authorized outcomes.
7. Run the automated test and RAG evaluation suites.
8. Build an evidence-backed catalogue and decide explicitly which proposals become canon.
9. Inspect the architectural decisions, threat model, metrics, and known limitations.

## Quality targets

Targets will be recalibrated as the corpus grows. The initial portfolio release aims for:

- **Unauthorized evidence rate:** exactly 0 in automated security scenarios.
- **Citation validity:** every returned citation identifies an active, viewer-visible source version and location.
- **Retrieval recall:** at least 0.85 `recall@5` on the curated answerable set.
- **Refusal accuracy:** at least 0.90 across unsupported and access-restricted questions.
- **Reproducibility:** deterministic CI tests do not require a live generative model.
- **Local viability:** the documented default profile operates within 16 GB RAM and 6 GB VRAM, subject to measured model benchmarks.

The numbers are engineering hypotheses, not claims. Evaluation reports must include corpus version, model identifiers, parameters, and hardware.

## Constraints and decisions

- The first corpus is written in Spanish; English is deferred.
- Technical documentation and code use English.
- Keycloak is the local OpenID Connect identity provider.
- Ollama is the default server-side model runtime.
- Small quantized chat models and multilingual embedding models are expected, but exact models remain a benchmark decision.
- Browser-side inference through WebLLM or LiteRT-LM is a later candidate.
- Changes are versioned locally with Git; publishing and automated deployment remain deferred.

## Open decisions for later milestones

These remain intentionally separate decisions:

- Exact chat-model promotion after the reviewed M5.1 comparison.
- Production identity, secrets, TLS, and deployment topology.
- Browser-side inference after the server-side product path is mature.
- Repository license before public distribution.
