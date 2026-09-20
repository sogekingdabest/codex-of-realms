# Baseline RAG evaluation

Use [IA.md](IA.md) for the end-to-end evaluation, its isolated environment and promotion criteria. [IA-V4.md](IA-V4.md) and [IA-V5.md](IA-V5.md) describe later experiments.

[`baseline.json`](baseline.json) records which source passages each actor may use and the expected result for each question. Backend tests and model evaluations share these cases.

## Dataset design

Cases cover six categories:

- `public_answerable`: visible evidence should support an answer.
- `privileged_answerable`: the actor may use restricted evidence.
- `access_restricted`: relevant evidence exists but is not visible; the result must be indistinguishable from absent evidence.
- `unsupported`: the corpus contains no sufficient evidence.
- `adversarial`: the question attempts to override policy or extract protected material.
- `authorization`: the actor cannot query the realm at all.

## Expected outcomes

- `ANSWERED`: the backend copies one to three original paragraphs from visible sources. Every excerpt references its citation and exact original offsets. The model returns only `outcome` and `passageIds`.
- `INSUFFICIENT_EVIDENCE`: the response must not reveal hidden facts, hidden source titles, or the existence of restricted material.
- `FORBIDDEN`: authorization fails before retrieval.

## Evaluation dimensions

### Retrieval

- `recall@k` against expected sources
- Unauthorized evidence rate
- Active-version and realm correctness
- Rank and distance distributions

### Answering

- Expected fact coverage
- Literal text and offsets checked against the original visible source
- Selection relevance (lexical fact coverage is a relevance proxy, not a truth validator)
- Citation validity and citation correctness
- Refusal accuracy
- Hidden fact leakage

## Versioning rules

- Keep case identifiers stable.
- Change `datasetVersion` when expected semantics change.
- Record the corpus commit, model identifiers, retrieval parameters, and hardware with every result.
- Do not replace an expected fact with exact prose matching.
- Any security failure blocks a release, regardless of the average quality score.

## Extractive evaluation (report version 3)

Reports use `EXTRACTIVE_SELECTION_WITH_ORACLE_VISIBLE_EVIDENCE`. The fixture ranks candidate passages (whole paragraphs or sentence windows) by question overlap within the actor-visible oracle sources, then applies the same six-candidate, three-excerpt limits and selection adapter as the application. This measures the selector and exact copying; it does not measure vector retrieval or replace authenticated authorization tests. Every answered case checks literal text and offsets. Historical report versions 1–2 describe generation and must not be reused as evidence for this contract.

Run `scripts/evaluate-local-models.ps1` with explicit installed model tags. It records rejection, relevance proxies, valid structured output and latency separately. No model downloads occur.

## Historical M5 coverage

`EvidenceGateBaselineTest` loads this dataset and verifies that every answerable Spanish case reaches generation while restricted, unsupported, and adversarial cases stop before the model. `RealmAuthorizationIntegrationTest` applies the same cases through the authenticated HTTP endpoint with deterministic embedding and chat doubles; the outsider remains non-disclosing and every accepted answer carries visible immutable citations.

Version 2 contains 22 cases over seven original Spanish sources. The integration suite writes the observed retrieval, refusal, citation, groundedness, and attack metrics to `backend/target/portfolio-reports/` for local review.
