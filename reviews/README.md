# Test results and reviews

Start with the most recent record for the area you want to inspect. Each report describes the revision tested on its date.

| Date | Record | Checks covered |
|---|---|---|
| 20 September | [Clean-install rehearsal](2026-09-20-ensayo-instalacion.md) | Published CI, fresh local data, three accounts, permissions, real ingestion, Atlas and two live queries |
| 20 September | [Local verification](../CHANGELOG.md#verification-recorded-on-20-september-2026) | 185 backend tests; 119 frontend tests, lint, coverage and builds |
| 19 September | [Archive interface](2026-09-19-archivo-campana.md) | Frontend checks and desktop/mobile review with example API data |
| 15 September | [Reading and navigation](2026-09-15-mejoras-portfolio.md) | Source workflows, deterministic checks and browser scenarios |
| 13 September | [AI context and omissions](2026-09-13-contexto-y-omisiones.md) | Candidate evaluation and the reasons promotion was blocked |
| 6 September | [Delivery acceptance](2026-09-06-entrega-y-validacion.md) | Deterministic, browser, recovery and selector results |

The initial local verification on 20 September did not repeat browser or live-model checks; the later rehearsal records those separately. Restore checks were not repeated. Use [OPERATIONS.md](../OPERATIONS.md) to run them.

## Older reports

Some findings were fixed later. For example, the 13 September portfolio review predates the 15 September reading, invitation, navigation and accessibility changes. Screenshots also show the interface as it looked when captured.

The JSON summaries beside the reports retain evaluated configurations and outcomes. Full runs under `demo/evaluation/results/` and browser output under `frontend/test-results/` are local artifacts.

Project screenshots are kept as dated evidence. External product images used in design research stay local; the public report links to their original sources.
