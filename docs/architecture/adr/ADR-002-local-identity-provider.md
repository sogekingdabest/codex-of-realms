# ADR-002: Use Keycloak as the local OpenID Connect provider

- **Status:** Accepted
- **Date:** 2026-08-26

## Context

Campaign roles and player-specific spoilers require a reliable identity for each request. Handling passwords and issuing tokens in the application would add security work unrelated to campaign knowledge.

The development environment also needs to run locally without a paid identity service.

## Decision

Run Keycloak in Docker Compose for local development and demonstrations. Configure the Spring Boot application as an OAuth 2.0 Resource Server that validates OpenID Connect-issued JWTs.

Keycloak authenticates users. Campaign memberships, roles and content grants remain in the application database.

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

Rejected because the demo needs to exercise a real login and token-validation flow.

### Hosted identity provider for the MVP

Rejected because the local environment should not depend on an external account or network service.

## References

- [Spring Security OAuth 2.0 Resource Server JWT documentation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Keycloak server container documentation](https://www.keycloak.org/server/containers)
