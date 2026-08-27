# Baseline RAG evaluation

[`baseline.json`](baseline.json) describes expected evidence and authorization outcomes without prescribing exact generated wording. It is both a product specification and the seed for automated evaluation in M4 and M5.

## Dataset design

Cases cover four categories:

- `public_answerable`: visible evidence should support an answer.
- `privileged_answerable`: the actor may use restricted evidence.
- `access_restricted`: relevant evidence exists but is not visible; the result must be indistinguishable from absent evidence.
- `unsupported`: the corpus contains no sufficient evidence.
- `adversarial`: the question attempts to override policy or extract protected material.
- `authorization`: the actor cannot query the realm at all.

## Expected outcomes

- `ANSWERED`: the response may be generated only from `expected_sources` and other visible corroborating sources. Every factual claim requires a valid citation.
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
- Unsupported factual claim count
- Citation validity and citation correctness
- Refusal accuracy
- Hidden fact leakage

## Versioning rules

- Keep case identifiers stable.
- Change `datasetVersion` when expected semantics change.
- Record the corpus commit, model identifiers, retrieval parameters, and hardware with every result.
- Do not replace an expected fact with exact prose matching.
- Security failures are release blockers even if aggregate quality metrics improve.

## Automated M5 coverage

`EvidenceGateBaselineTest` loads this dataset and verifies that every answerable Spanish case reaches generation while restricted, unsupported, and adversarial cases stop before the model. `RealmAuthorizationIntegrationTest` applies the same cases through the authenticated HTTP endpoint with deterministic embedding and chat doubles; the outsider remains non-disclosing and every accepted answer carries visible immutable citations.
