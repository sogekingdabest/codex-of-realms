# Ubiquitous Language

> Historical record. See [current documentation](../../README.md) for setup and behavior.

These definitions connect the terms used in the interface, documentation, API, and code.

## Product and tenancy

### Codex of Realms

The product. `Codex` is not a domain object.

### Realm

The top-level knowledge and authorization boundary. During the MVP, a realm may represent either a fictional world or a specific campaign. A world-to-campaign hierarchy is outside the MVP.

### Member

An authenticated user associated with a realm through a membership.

### Membership

The realm-scoped relationship between a user and a role. Global application roles do not replace membership checks.

### Role

The member's realm-level authority: `OWNER`, `EDITOR`, or `PLAYER`.

## Sources and ingestion

### Source document

The logical identity of an uploaded lore source across replacements.

### Document version

An immutable snapshot of a source document, including checksum, media type, language, storage reference, and processing state.

### Chunk

A source-derived text fragment used for retrieval. A chunk retains its document version, ordinal, structural heading, source location, access policy, and embedding provenance.

### Ingestion

The controlled process that validates, reads, splits, embeds, and indexes a document version.

### Reprocessing

Running ingestion again from the active version's immutable raw bytes, normally after an implementation or model change. A changed pipeline creates a new immutable version; an unchanged pipeline is a no-op.

## Knowledge and canon

### Lore entity

A named character, place, faction, object, or event managed in the structured catalogue.

### Lore relation

A typed, directional claim connecting two lore entities. A relation is not necessarily canonical unless its canon status says so.

### Canon

Knowledge explicitly accepted as true within a realm.

### Proposed content

Human- or AI-authored material that has not been promoted to canon. It must be visibly distinguishable from canonical material.

### Canon status

The controlled state of a knowledge item: initially `CANON` or `PROPOSED`. Model output defaults to `PROPOSED` if it is persisted at all.

### Provenance

Information that explains where a claim came from. For source-backed claims this includes an immutable document version and location.

## Access

### Access policy

The rule that determines which realm members may access content.

### Public

`PUBLIC` means visible to every active member of the realm. It does not mean internet-public.

### Game Master only

`GM_ONLY` means visible to the realm owner and permitted editors, but not to players.

### Spoiler

`SPOILER` means hidden by default and visible only to privileged roles or explicitly granted members. Spoiler access is enforced data, not presentation metadata.

### Effective access

The access decision produced from authenticated identity, active membership, role, content policy, and explicit grants.

## Retrieval and answering

### Lore question

A natural-language request evaluated within exactly one realm and authenticated viewer perspective.

### Candidate evidence

An authorized chunk considered by retrieval for a lore question.

### Retrieved evidence

Candidate evidence selected and ranked for possible inclusion in the model context.

### Evidence gate

A deterministic decision that determines whether retrieved evidence is sufficient to attempt an answer. Its thresholds are calibrated, not assumed.

### Grounded answer

An answer whose factual claims are supported by the supplied retrieved evidence.

### Insufficient evidence

The explicit outcome `INSUFFICIENT_EVIDENCE`. It does not reveal whether inaccessible evidence exists.

### Citation

A validated reference from an answer to a visible chunk and its immutable source location.

### Refusal accuracy

The proportion of unsupported or access-restricted evaluation questions that correctly produce an insufficient-evidence outcome.

### Retrieval recall at k

The proportion of expected relevant source items found among the first `k` authorized retrieval results.
