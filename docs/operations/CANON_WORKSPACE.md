# Canon workspace

M9 adds **Atlas del canon** to the Spanish first-party web application. It turns the access-aware M6 catalogue into a
complete browser workflow and does not require Ollama or installed model weights.

## Curator workflow

Authenticate as a realm `OWNER` or `EDITOR`, select a realm, and open **Atlas del canon**.

1. Choose **Entidades** and create a character, place, faction, object, or event.
2. Select an access policy. If evidence is needed, choose a `READY` source with that same policy and mark one or more
   exact fragments.
3. Save the entity. New and edited claims are always **Propuestos**.
4. Review the description, policy, and evidence, then use **Promover a canon** as a separate human decision.
5. Open **Relaciones**, choose two existing entities, name the directional claim, and repeat the evidence and promotion
   workflow.

Editing a canonical record deliberately returns it to **Propuesto**. Deleting an entity with active relations is
rejected; remove the relations first. Evidence is rendered as text and never interpreted as HTML.

## Player workflow

A `PLAYER` can search and filter entities and relations, inspect their canon state, and open the evidence snapshots the
server authorizes. Creation, editing, promotion, deletion, policy listing, and source-chunk discovery are absent from
the player workflow and remain protected by backend authorization.

Relations are visible only when the player can access the relation policy and both endpoint entities. Hidden and
cross-realm records are non-disclosing.

## Deliberate boundaries

- The model does not extract, modify, or promote catalogue records.
- Evidence is optional, but evidence-backed claims are preferable when a source exists.
- All evidence attached to one claim must use the same access policy as that claim.
- Retiring a source keeps the immutable evidence snapshot; the full raw source is no longer openable afterward.
- Pagination, graph visualization, contradiction detection, and automatic timelines remain later product decisions.

## Verification

Run the deterministic suites without local model weights:

~~~powershell
cd backend
.\mvnw.cmd --batch-mode --no-transfer-progress verify

cd ..\frontend
npm run lint
npm run test
npm run build
~~~

The backend suite verifies editor-only chunk discovery, player denial, role and visibility boundaries, evidence
provenance, canon transitions, cross-realm rejection, and retirement behavior. Frontend tests verify player read-only
rendering, evidence-backed creation, explicit promotion, navigation, and typed API requests.
