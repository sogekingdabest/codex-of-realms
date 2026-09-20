import { useEffect, useRef } from 'react'
import type { ContentApi, SourceEvidence } from '../../content'
import type { AccessPolicyView } from '../../realm'
import { CanonBadge, CatalogueEvidence } from '../CataloguePrimitives'
import { entityTypeLabels } from '../catalogueModel'
import type { LoreEntityView, LoreRelationView } from '../model'
import { EvidencePreview } from '../evidence/EvidencePreview'

export function EntityDocument({ entity, contentApi, relations, policies, canEdit, saving, focusHeading, onOpenEntity, onOpenEvidence, onEdit, onPromote, onDelete }: Readonly<{
  entity: LoreEntityView
  contentApi: ContentApi
  relations: LoreRelationView[]
  policies: AccessPolicyView[]
  canEdit: boolean
  saving: boolean
  focusHeading: boolean
  onOpenEntity: (id: string) => void
  onOpenEvidence: (evidence: SourceEvidence) => void
  onEdit: (entity: LoreEntityView) => void
  onPromote: (entity: LoreEntityView) => Promise<void>
  onDelete: (entity: LoreEntityView) => Promise<void>
}>) {
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => { if (focusHeading) heading.current?.focus() }, [entity.id, focusHeading])
  const related = relations.filter((relation) => relation.sourceEntityId === entity.id || relation.targetEntityId === entity.id)
  const policy = policies.find((item) => item.id === entity.accessPolicyId)

  return <div className="entity-reading-layout">
    <article className="entity-document">
      <header className="entity-document-heading">
        <div className="entity-document-meta"><span>{entityTypeLabels[entity.type]}</span><CanonBadge status={entity.canonStatus} /></div>
        <h2 ref={heading} tabIndex={-1}>{entity.displayName}</h2>
        {entity.aliases.length > 0 && <p className="aliases">También: {entity.aliases.join(' · ')}</p>}
      </header>
      <p className="entity-description">{entity.description}</p>
      <section className="entity-relations" aria-label="Relaciones de la ficha">
        <h3>Relaciones</h3>
        {related.length === 0 ? <p className="muted">Sin relaciones visibles registradas.</p> : related.map((relation) => {
          const outgoing = relation.sourceEntityId === entity.id
          const otherName = outgoing ? relation.targetEntityName : relation.sourceEntityName
          return <div className="entity-relation" key={relation.id}>
            <button type="button" className="entity-relation-link" aria-label={`Ver ficha de ${otherName}`}
              onClick={() => onOpenEntity(outgoing ? relation.targetEntityId : relation.sourceEntityId)}>
              <span>{otherName}</span><span aria-hidden="true">↗</span>
            </button>
            <p>{outgoing ? 'Hacia' : 'Desde'} {otherName} · {relation.relationType.replaceAll('_', ' ').toLocaleLowerCase('es')} · {relation.canonStatus === 'CANON' ? 'Canon' : 'Propuesto'}</p>
            <p className="relation-description">{relation.description}</p>
          </div>
        })}
      </section>
      <footer className="entity-document-footer">
        <span>{policy?.name ?? 'Visibilidad autorizada'} · {entity.promotionHistory.length === 1 ? '1 promoción' : `${entity.promotionHistory.length} promociones`}</span>
        {canEdit && <div className="entity-actions">
          <button className="citation-link" disabled={saving} type="button" onClick={() => onEdit(entity)}>Editar</button>
          {entity.canonStatus === 'PROPOSED' && <button className="citation-link" disabled={saving} type="button" onClick={() => void onPromote(entity)}>Promover a canon</button>}
          <button className="text-danger" disabled={saving} type="button" onClick={() => void onDelete(entity)}>Retirar</button>
        </div>}
      </footer>
    </article>
    <aside className="entity-context" aria-label="Fuentes de la ficha">
      <h3>Fuentes vinculadas</h3>
      <CatalogueEvidence evidence={entity.sourceEvidence.slice(0, 1)} onOpen={onOpenEvidence} />
      {entity.sourceEvidence[0] && <EvidencePreview key={`${entity.realmId}:${entity.sourceEvidence[0].chunkId}:${entity.sourceEvidence[0].documentVersionId}:${entity.sourceEvidence[0].startOffset}:${entity.sourceEvidence[0].endOffset}`} contentApi={contentApi} realmId={entity.realmId} evidence={entity.sourceEvidence[0]} />}
      {entity.sourceEvidence.length > 1 && <CatalogueEvidence evidence={entity.sourceEvidence.slice(1)} onOpen={onOpenEvidence} />}
      <p className="context-note">El canon se decide de forma explícita. Abre una fuente para contrastar el fragmento vinculado.</p>
    </aside>
  </div>
}
