# Changelog

## 0.1.0-beta — 2 October 2026

The first beta: a local installation for a Game Master and a few players. Setup is in the [README](README.md#run-locally), the demo campaign loads with one script, and the [current limitations](docs/product/LIMITATIONS.md) list what to expect.

- Invitations accepted through verified email and campaign-owned permissions.
- Persistent source jobs with idempotent submissions, retries and versioned publication.
- Answers built from authorized source excerpts with citations. The models warm up at startup, and a slow question shows how long it has waited and can be cancelled.
- Source reading and title/filename search, multiple universes and atlas navigation.
- Campaign archive layout with keyboard and mobile reading support. Each record shows who can see it.
- Registration, sign-in and verification emails in the application's own theme, with Spanish and English texts.
- A demo loader that creates El Meridiano de Ceniza with three accounts, seven sources, a spoiler group and an atlas, then checks what each account sees.
- Revised documentation, MIT license and contribution/security guides.

### Verification recorded on 2 October 2026

On commit `8765c9b`, [CI](https://github.com/sogekingdabest/codex-of-realms/actions/runs/37028219209) passed backend `verify` with 200 tests, frontend `npm run verify` with 216 tests, the Compose definitions and the browser scenarios, including the demo loader on Linux. A [clean-install rehearsal](reviews/2026-10-02-ensayo-instalacion.md) of the same commit loaded the demo campaign with the real models, answered live questions without leaking restricted content, and passed the backup and restore check.

### Verification recorded on 20 September 2026

Backend `verify` passed 185 tests with no failures, errors or skips, and built the application package. Frontend `npm run verify` passed lint, 119 tests, coverage thresholds and the production build.

Browser, restore and live-model checks were not repeated. The documentation revisions that followed did not change application behavior.

## Earlier work

| Date | Record | Main change |
|---|---|---|
| 2026-10-01 | [Simulated user session](reviews/2026-10-01-sesion-simulada.md) | Mobile results, evidence dialog, registration, library and Game Master fixes |
| 2026-09-20 | [Clean-install rehearsal](reviews/2026-09-20-ensayo-instalacion.md) | First installation from a clean clone and a model name fix |
| 2026-09-19 | [Campaign archive](reviews/2026-09-19-archivo-campana.md) | Reading layout and frontend checks |
| 2026-09-15 | [Portfolio usability](reviews/2026-09-15-mejoras-portfolio.md) | Source reading, navigation and browser scenarios |
| 2026-09-13 | [Context and omissions](reviews/2026-09-13-contexto-y-omisiones.md) | AI candidate evaluated; experimental options stayed disabled |
| 2026-09-06 | [Delivery acceptance](reviews/2026-09-06-entrega-y-validacion.md) | Deterministic, browser, recovery and selector measurements |

The [review index](reviews/README.md) explains the scope of each run. The [historical roadmap](docs/archive/ROADMAP.md) covers M0–M9.
