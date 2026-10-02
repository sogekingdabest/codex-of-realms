# Roadmap

The first beta, [0.1.0-beta](CHANGELOG.md#010-beta--2-october-2026), is a documented local installation for small role-playing groups. The remaining work is learning where people need help.

## First beta

- [x] Review and commit the delivery, then install it from a clean clone ([2 October rehearsal](reviews/2026-10-02-ensayo-instalacion.md)).
- [x] Run backend, frontend, browser and backup/restore checks on the same release candidate (`8765c9b`).
- [x] Prepare an example campaign that a newcomer can explore immediately: `scripts/load-demo-campaign.ps1`.
- [x] Publish a tagged beta with setup instructions, known limitations and a short demonstration: `v0.1.0-beta`.

## Next

- [ ] Run the [user session](docs/product/USER_VALIDATION.md) with a Game Master and two players, then fix the tasks that repeatedly need help. Until a group is available, scripted sessions with real models stand in for it ([1 October](reviews/2026-10-01-sesion-simulada.md)).
- [ ] Decide whether a hosted beta by invitation is needed. Hosting needs HTTPS, identity and email configuration, private service networks and resource limits.

AI experiments have their own promotion criteria. The [evaluation guide](demo/evaluation/README.md) describes them.

## After feedback

Likely follow-ups are pagination for source-job history, better source discovery, and further work on context selection and omissions. Export and campaign/account lifecycle needs should be settled before a hosted service holds long-lived user data.

PDF/OCR, automatic extraction and timelines remain outside the [initial scope](docs/product/MVP_SCOPE.md). Add them when observed use makes the need clear.

The [M0–M9 roadmap](docs/archive/ROADMAP.md) records earlier development.
