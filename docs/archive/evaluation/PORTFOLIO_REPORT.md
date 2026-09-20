# M7 deterministic quality report

> Historical record. See [current documentation](../../README.md) for setup and behavior.

- **Verified:** 2026-08-28
- **Dataset:** `demo/evaluation/baseline.json`, version 2
- **Execution:** deterministic embeddings and deterministic chat double
- **Database:** disposable PostgreSQL 18/pgvector 0.8.6 through Testcontainers

## Results

| Metric | Target | Observed |
|---|---:|---:|
| Retrieval recall@10 | >= 0.90 | 1.000 |
| Refusal accuracy | 1.00 | 1.000 (8/8) |
| Citation correctness | 1.00 | 1.000 (13/13 answered cases) |
| Validated grounded-answer rate | 1.00 | 1.000 (13/13 answered cases) |
| Security attack pass rate | 1.00 | 1.000 (8/8) |

The corpus contains seven original Spanish sources and the dataset contains 22 cases: 13 answerable, eight expected refusals, and one unauthorized outsider request. The security result covers three hidden-content probes, three direct prompt-injection variants, the outsider request, and one indirect prompt-injection source fixture.

## Meaning and limitations

Every accepted citation points to evidence retrieved for the same authenticated actor and realm. Every deterministic answer passed the claim-and-citation validator. These results measure the test double and application checks; real-model faithfulness requires a separate evaluation.

This report proves application orchestration, access filtering, abstention, and citation invariants without network access or model downloads. Real-model quality and latency remain a separate M5.1 evaluation; use `scripts/evaluate-local-models.ps1` before promoting a chat model.

## Reproduce

From `backend`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress verify
```

The test writes machine-readable and Markdown results to `backend/target/portfolio-reports/`. Maven also generates Spring Modulith diagrams and module canvases in `backend/target/spring-modulith-docs/`.
