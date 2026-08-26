# ADR-004: Fix the M1 technology baseline

- **Status:** Accepted
- **Date:** 2026-08-26

## Context

The walking skeleton needs a compatible, reproducible baseline before domain implementation begins. The application must run on Java 21, verify module boundaries, authenticate through OIDC, use PostgreSQL with pgvector, and keep local model execution optional. CI must not download or execute AI models.

## Decision

Use the following M1 baseline:

- Eclipse Temurin JDK 21 for development and CI
- Spring Boot 4.1.1
- Spring AI 2.0.1
- Spring Modulith 2.1.1
- springdoc-openapi 3.1.0
- PostgreSQL 18 with pgvector 0.8.6
- Keycloak 26.7.2
- Ollama 0.32.5
- Maven Wrapper 3.3.4 using Maven 3.9.16

Versions are centralized in the Maven build or fixed in Docker Compose. Spring AI chat and embedding auto-configuration is disabled in M1; Ollama is present as an infrastructure boundary but no model is pulled automatically. PostgreSQL integration is exercised with Testcontainers, while Spring Modulith verifies package boundaries.

Application code targets the Java 21 standard and must not depend on Temurin-specific APIs. Choosing the same distribution locally and in CI reduces environment drift without creating a runtime vendor lock-in.

## Consequences

- A clean environment can use the checked-in wrapper without a system Maven installation.
- Local startup is repeatable and does not silently download large models.
- Framework and container upgrades are explicit maintenance changes.
- Docker is required for the PostgreSQL integration suite.
- Model selection remains an evaluated M4/M5 decision rather than an M1 guess.
