# Changelog

## Unreleased — first beta preparation

- Invitations accepted through verified email and campaign-owned permissions.
- Persistent source jobs with idempotent submissions, retries and versioned publication.
- Answers built from authorized source excerpts with citations.
- Source reading and title/filename search, multiple universes and atlas navigation.
- Campaign archive layout with keyboard and mobile reading support.
- Revised documentation, MIT license and contribution/security guides.

### Verification recorded on 20 September 2026

Backend `verify` passed 185 tests with no failures, errors or skips, and built the application package. Frontend `npm run verify` passed lint, 119 tests, coverage thresholds and the production build.

Browser, restore and live-model checks were not repeated. The documentation revisions that followed did not change application behavior.

## Earlier work

| Date | Record | Main change |
|---|---|---|
| 2026-09-19 | [Campaign archive](reviews/2026-09-19-archivo-campana.md) | Reading layout and frontend checks |
| 2026-09-15 | [Portfolio usability](reviews/2026-09-15-mejoras-portfolio.md) | Source reading, navigation and browser scenarios |
| 2026-09-13 | [Context and omissions](reviews/2026-09-13-contexto-y-omisiones.md) | AI candidate evaluated; experimental options stayed disabled |
| 2026-09-06 | [Delivery acceptance](reviews/2026-09-06-entrega-y-validacion.md) | Deterministic, browser, recovery and selector measurements |

The [review index](reviews/README.md) explains the scope of each run. The [historical roadmap](docs/archive/ROADMAP.md) covers M0–M9.
