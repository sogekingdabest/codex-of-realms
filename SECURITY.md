# Security

The supplied deployment is for local development. Read the [threat model](docs/security/THREAT_MODEL.md) before exposing it to other users.

## Report a vulnerability

Use **Security → Report a vulnerability** on GitHub if the repository has that private channel enabled. If it is unavailable, open an issue asking for a private contact without describing the vulnerability.

In the private report, include the affected revision, your deployment setup and a minimal reproduction with fictional data. Keep credentials, tokens and private campaign content out of public issues.

There is no response-time commitment or supported-release policy yet. A permissions bypass or disclosure of hidden campaign material blocks a release.

## Run safely

Keep PostgreSQL, Ollama, Mailpit, administration and management endpoints off untrusted networks. Store `.env` and backups privately. A public deployment needs HTTPS, production identity and email settings, and resource limits; the [deployment limitations](docs/product/LIMITATIONS.md#accounts-and-deployment) describe the remaining work.
