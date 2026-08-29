# Code quality

The repository measures Java and TypeScript coverage locally and sends the resulting reports to two SonarQube Cloud projects. Sonar remains advisory until the calibration criteria below are satisfied.

## Local verification

Run the backend verification from `backend`:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress verify
~~~

JaCoCo writes its XML report to `backend/target/site/jacoco/jacoco.xml`. The build deliberately has no repository-wide coverage threshold; the Sonar way quality gate evaluates new code instead.

Run the frontend verification from `frontend`:

~~~powershell
npm ci
npm run lint
npm run test:coverage
npm run build
~~~

Vitest writes LCOV data to `frontend/coverage/lcov.info`. Both coverage directories are ignored by Git.

## SonarQube Cloud onboarding

1. Create a Free organization in the SonarQube Cloud EU region and connect it to the GitHub organization that owns this repository.
2. Import this repository twice using the monorepo flow. Name the projects **Codex of Realms — Backend** and **Codex of Realms — Frontend**.
3. Disable automatic analysis for both projects. CI-based analysis is required to import JaCoCo and LCOV reports.
4. Assign the built-in **Sonar way** quality gate and set **Administration > New Code > Number of days** to `30` in both projects.
5. Generate a personal analysis token that can execute analysis for both projects.
6. Add the following repository configuration in GitHub:

| Kind | Name | Initial value |
| --- | --- | --- |
| Secret | `SONAR_TOKEN` | Personal Sonar analysis token |
| Variable | `SONAR_ORGANIZATION` | SonarQube Cloud organization key |
| Variable | `SONAR_BACKEND_PROJECT_KEY` | Backend project key |
| Variable | `SONAR_FRONTEND_PROJECT_KEY` | Frontend project key |
| Variable | `SONAR_ENFORCE` | `false`; later `true`, or `backend` for the compatibility fallback |

The workflow uses the EU service by default, so it does not set `sonar.region`. Checkout uses the complete Git history so Sonar can attribute and compare new code correctly.

## Calibration and enforcement

Keep `SONAR_ENFORCE=false` until all of the following are true:

- the analysis of `main` succeeds for both projects;
- JaCoCo and LCOV coverage appear in the respective dashboards;
- three representative pull requests have been analyzed;
- every security hotspot introduced by those pull requests has been reviewed;
- there are no unresolved scanner errors or TypeScript compatibility failures.

Then change `SONAR_ENFORCE` to `true`. The existing `web` and `verify` jobs wait for their corresponding quality gate and fail when the gate is red, the scan fails, or required Sonar configuration is missing. Pull requests from forks do not receive repository secrets; run their final validation from an internal branch before merging.

Use `SONAR_ENFORCE=backend` only for the TypeScript compatibility fallback described below. That value makes the backend gate blocking while the frontend scan remains advisory.

## TypeScript 6 compatibility

The frontend remains on TypeScript 6.0.3 even while Sonar's documented support trails that version. During calibration:

1. Keep the frontend scan advisory if it completes with compatibility warnings.
2. If analysis fails while loading the TypeScript projects, add a `tsconfig.sonar.json` containing the supported compiler options from `tsconfig.app.json`, exclude build-only configuration, and point `sonar.typescript.tsconfigPaths` to it.
3. If parsing still fails, set `SONAR_ENFORCE=backend` until Sonar supports the frontend toolchain. Do not downgrade the production TypeScript compiler solely for analysis.

## Troubleshooting

- A missing JaCoCo or LCOV file fails the normal verification job before Sonar runs.
- With `SONAR_ENFORCE=false`, scanner and quality-gate failures are visible but do not fail the job.
- With `SONAR_ENFORCE=backend`, only the Java scanner and quality gate are blocking.
- With `SONAR_ENFORCE=true`, missing secrets or project keys fail explicitly instead of silently skipping analysis.
- SonarQube Cloud does not generate coverage; rerun the local coverage command if the dashboard reports zero or cannot find the report.

See the official SonarQube Cloud documentation for [GitHub Actions](https://docs.sonarsource.com/sonarqube-cloud/advanced-setup/ci-based-analysis/github-actions-for-sonarcloud), [Java coverage](https://docs.sonarsource.com/sonarqube-cloud/enriching/test-coverage/java-test-coverage), and [JavaScript/TypeScript coverage](https://docs.sonarsource.com/sonarqube-cloud/enriching/test-coverage/javascript-typescript-test-coverage).
