# ADR-001: Start as a package-modular monolith

- **Status:** Accepted
- **Date:** 2026-08-26

## Context

Realm management, ingestion, retrieval and the lore catalogue have different responsibilities. For the MVP, however, they share one developer, one deployment target and one database. None yet needs independent scaling.

Separating them into services would add network failures, contract versioning and deployment coordination before the campaign workflow has been tried by users.

## Decision

Build one Spring Boot application and one PostgreSQL database. Organize the code by business capability, with explicit module APIs and internal domain, application, infrastructure, and web layers.

The initial logical modules are:

- `realm`: realm lifecycle, memberships, roles, grants, and effective access
- `content`: source documents, versions, storage, and ingestion
- `lore`: structured entities, relations, provenance, and canon state
- `qa`: access-aware retrieval, evidence gating, generation, and citations

Use Spring Modulith tests to verify module boundaries and document dependencies. Keep infrastructure adapters replaceable at meaningful external boundaries, especially identity, file storage, embedding, chat generation, and retrieval.

Use one Maven module and check business-module boundaries at package level.

## Dependency rules

- Modules interact through explicit public interfaces or application events.
- A module must not access another module's repositories or internal domain types.
- `content` does not depend on `qa`; answering consumes published content contracts.
- Realm authorization is invoked explicitly at use-case and query boundaries.
- `shared` contains only truly cross-cutting primitives and must not become a miscellaneous domain module.
- Cross-module events remain in-process until a demonstrated need requires durable asynchronous delivery.

## Consequences

### Positive

- Simple local startup and debugging.
- Atomic transactions for the first workflows.
- Architectural boundaries remain testable.
- The application can later extract a module using observed coupling and load data.

### Negative

- Poor package discipline could turn the application into a coupled monolith.
- All modules share a release cadence and process resources.
- Future extraction may require data migration and new consistency rules.

### Mitigation

- Add module-boundary tests in M1.
- Keep module-owned tables and migrations identifiable.
- Review module dependencies in every architecture-affecting change.
- Record the conditions that would justify extracting a service.

## Rejected alternatives

### Microservices from the start

Rejected because no independent scaling, ownership, availability, or deployment requirement exists.

### Multiple Maven modules from the start

Rejected because it adds build complexity without replacing package-level dependency discipline.

### Separate Python AI service

Rejected for the MVP because Spring AI covers the first ingestion, embedding, and generation requirements. It can be reconsidered for evaluated reranking or extraction workloads.

## References

- [Spring Modulith fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html)
- [Spring Modulith documentation generation](https://docs.spring.io/spring-modulith/reference/documentation.html)
