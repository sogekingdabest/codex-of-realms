# Design choices

These notes describe the implementation on 20 September 2026. The [ADRs](adr/README.md) retain the reasoning and alternatives considered during development.

## Keep transactions and deployment manageable

A single Spring Boot application and PostgreSQL instance fit the project's size. Module APIs keep business responsibilities separate, while a shared transaction can protect changes such as transferring ownership or publishing a source version.

PostgreSQL also stores processing jobs. That avoids operating a message broker, but puts job queries and campaign data on the same database. The current job-history listing needs pagination as usage grows.

## Apply permissions where data is read

Roles and spoiler grants change as a campaign progresses. Retrieval uses those database records before vector ranking, so hidden chunks never become model context. The same principle applies to source readers, citations and atlas relations.

This makes the SQL adapter more specific than a generic vector-store API. The benefit is that membership, active versions and grants can be checked in the same query. Every new reading path must preserve those checks.

## Keep originals and limit model authority

Replacing a document creates a version rather than overwriting its bytes. Readers keep the published version until its replacement is ready, and citations retain their source coordinates. Backups therefore need both database state and original files.

The earlier answer pipeline asked the model to generate claims. The current one asks it for passage identifiers and builds excerpts in the backend. Copying text makes provenance verifiable, but a selection can still omit context or answer the wrong part of a question. The atlas keeps human control over canon through a separate editing and promotion workflow.

## Measure local models separately

Ollama keeps campaign processing local, at the cost of model downloads, hardware requirements and cold-start latency. Application ports leave room for another provider.

CI uses deterministic model doubles so it can reproduce API and permission behavior. Live evaluations measure selection quality and speed. The [v5 report](../../reviews/2026-09-13-contexto-y-omisiones.md) explains why hybrid retrieval, grouped context and omission checks remain disabled after the candidate missed its promotion criteria.

For implementation details, see [modules](MODULES.md), [authorization](../security/AUTHORIZATION_MODEL.md) and [operations](../../OPERATIONS.md).
