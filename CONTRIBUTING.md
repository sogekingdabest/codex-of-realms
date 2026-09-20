# Contributing

Use an issue to describe a reproducible bug or propose a focused feature. Include what you were trying to do and what got in the way. Security reports should follow [SECURITY.md](SECURITY.md).

For setup, see [local development](docs/operations/LOCAL_DEVELOPMENT.md). Java 21, Node.js 24 and Docker are the main requirements. The [architecture](docs/architecture/ARCHITECTURE.md) and [MVP scope](docs/product/MVP_SCOPE.md) explain where a change belongs.

## Before opening a pull request

- For backend changes, run `./mvnw --batch-mode --no-transfer-progress verify` from `backend/` (`.\mvnw.cmd` on Windows), with Docker running.
- For frontend changes, run `npm ci` and `npm run verify` from `frontend/`.
- If a change affects login or a user journey, run the [browser suite](OPERATIONS.md#reproducible-acceptance).
- Update tests for changed behavior, including denied-access cases when permissions are involved.
- Update the guide that describes any changed command or public behavior.

In the PR description, explain the problem, the resulting behavior and which checks you ran. Use fictional test data and leave credentials, real campaign content and backups out of the repository.

Model changes also need live evaluation. Keep frozen datasets unchanged, and leave experiments disabled until their recorded acceptance criteria pass.

Contributions use the project's [MIT license](LICENSE).
