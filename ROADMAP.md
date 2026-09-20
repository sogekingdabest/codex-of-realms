# Roadmap

The next release is a small beta for role-playing groups. The core workflow is in place; the remaining work is making it easy to try and learning where users need help.

## Before the first beta

- [ ] Review and commit the delivery, then install it from a clean clone.
- [ ] Run backend, frontend, browser and backup/restore checks on the same release candidate.
- [ ] Prepare an example campaign that a newcomer can explore immediately.
- [ ] Choose between a documented local installation and a hosted beta by invitation. Hosting needs HTTPS, identity and email configuration, private service networks and resource limits.
- [ ] Run the [user session](docs/product/USER_VALIDATION.md) with a Game Master and two players, then fix the tasks that repeatedly need help.
- [ ] Publish a tagged beta with setup instructions, known limitations and a short demonstration.

AI experiments have their own promotion criteria. The [evaluation guide](demo/evaluation/README.md) describes them.

## After feedback

Likely follow-ups are pagination for source-job history, better source discovery, and further work on context selection and omissions. Export and campaign/account lifecycle needs should be settled before a hosted service holds long-lived user data.

PDF/OCR, automatic extraction and timelines remain outside the [initial scope](docs/product/MVP_SCOPE.md). Add them when observed use makes the need clear.

The [M0–M9 roadmap](docs/archive/ROADMAP.md) records earlier development.
