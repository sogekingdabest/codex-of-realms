# ADR-002: Use Keycloak as the local OpenID Connect provider

- **Status:** Accepted
- **Date:** 2026-08-26

## Context

Realm isolation, role-based access, and player-specific spoilers require real authenticated identities. Implementing password storage and token issuance inside the application would expand the security surface and distract from the product's domain.

The complete development environment must be runnable locally and reproducible without a paid identity service.

## Decision

Run Keycloak in Docker Compose for local development and demonstrations. Configure the Spring Boot application as an OAuth 2.0 Resource Server that validates OpenID Connect-issued JWTs.

The application owns realm memberships, realm roles, and content grants. Keycloak proves identity but does not become the source of realm authorization policy.

Stable external subject identifiers are mapped to local users. Application endpoints never trust realm identifiers, roles, or spoiler grants submitted as client claims unless the application explicitly issued and validates that claim contract.

## Consequences

### Positive

- Avoids implementing password and session security.
- Provides realistic token validation and identity flows.
- Enables reproducible Game Master and player demo identities.
- Keeps domain authorization testable independently from the identity provider.

### Negative

- Adds a relatively heavy local container.
- Requires realm/client configuration and key rotation handling.
- Authentication integration tests need a balance between real-provider tests and faster JWT fixtures.

### Mitigation

- Version a development realm import with non-secret demo identities only.
- Use issuer and audience validation.
- Test authorization primarily at the application boundary with signed test JWTs, plus a smaller end-to-end Keycloak suite.
- Never treat Keycloak realm roles as a substitute for Codex realm membership.

## Rejected alternatives

### Application-issued username/password JWTs

Rejected because it creates avoidable credential storage and token lifecycle responsibilities.

### Mock authentication only

Rejected because it would not demonstrate a reproducible realistic identity boundary.

### Hosted identity provider for the MVP

Rejected because the local environment should not depend on an external account or network service.

## References

- [Spring Security OAuth 2.0 Resource Server JWT documentation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Keycloak server container documentation](https://www.keycloak.org/server/containers)
