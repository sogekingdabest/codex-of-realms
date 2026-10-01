import { useEffect, useRef } from 'react'
import type { VisibilityAudience } from '../../../shared/lib/visibility'
import { Link } from '../../../shared/routing'
import { DangerButton } from '../../../shared/ui/DangerButton'
import { VisibilityMark } from '../../../shared/ui/VisibilityMark'
import type { ContentApi, SourceEvidence } from '../../content'
import { CatalogueEvidence } from '../CataloguePrimitives'
import { CanonMark, CanonSeal } from '../CanonSeal'
import { entityTypeLabels, promotionLabel, relationVerb } from '../catalogueModel'
import { EntityTypeIcon } from '../EntityTypeIcon'
import type { LoreEntityView, LoreRelationView } from '../model'
import { EvidencePreview } from '../evidence/EvidencePreview'

export function EntityDocument({ entity, contentApi, relations, audience, canEdit, saving, focusHeading, entityHref, onOpenEvidence, onEdit, onPromote, onDelete }: Readonly<{
  entity: LoreEntityView
  contentApi: ContentApi
  relations: LoreRelationView[]
  audience: VisibilityAudience
  canEdit: boolean
  saving: boolean
  focusHeading: boolean
  entityHref: (id: string) => string
  onOpenEvidence: (evidence: SourceEvidence) => void
  onEdit: (entity: LoreEntityView) => void
  onPromote: (entity: LoreEntityView) => Promise<void>
  onDelete: (entity: LoreEntityView) => Promise<void>
}>) {
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => { if (focusHeading) heading.current?.focus() }, [entity.id, focusHeading])
  const related = relations.filter((relation) => relation.sourceEntityId === entity.id || relation.targetEntityId === entity.id)
  const visibility = <VisibilityMark visibility={entity.visibility} viewer={audience.viewer} revealedTo={audience.revealedTo(entity.accessPolicyId)} />
  const [firstEvidence, ...otherEvidence] = entity.sourceEvidence

  return <div className="entity-reading-layout">
    <article className="entity-document">
      <header className="entity-document-heading">
        <div className="entity-document-meta">
          <span className="entity-type"><EntityTypeIcon type={entity.type} size={15} />{entityTypeLabels[entity.type]}</span>
          {visibility}
        </div>
        <div className="entity-document-title">
          <h2 ref={heading} tabIndex={-1}>{entity.displayName}</h2>
          <CanonSeal status={entity.canonStatus} />
        </div>
        {entity.aliases.length > 0 && <p className="aliases">También: {entity.aliases.join(' · ')}</p>}
      </header>
      <p className="entity-description">{entity.description}</p>
      <section className="entity-relations" aria-label="Relaciones de la ficha">
        <h3>Relaciones</h3>
        {related.length === 0 ? <p className="muted">Sin relaciones visibles registradas.</p> : related.map((relation) => {
          const outgoing = relation.sourceEntityId === entity.id
          const otherId = outgoing ? relation.targetEntityId : relation.sourceEntityId
          const otherName = outgoing ? relation.targetEntityName : relation.sourceEntityName
          const other = <Link className="relation-entity" aria-label={`Ver ficha de ${otherName}`} href={entityHref(otherId)}>{otherName}</Link>
          const self = <strong className="relation-entity">{entity.displayName}</strong>
          return <div className="entity-relation" key={relation.id}>
            <div className="relation-heading">
              <p className="relation-sentence">{outgoing ? self : other} <span className="relation-verb">{relationVerb(relation.relationType)}</span> {outgoing ? other : self}</p>
              <span className="relation-marks">
                <CanonMark status={relation.canonStatus} />
                <VisibilityMark visibility={relation.visibility} viewer={audience.viewer} revealedTo={audience.revealedTo(relation.accessPolicyId)} />
              </span>
            </div>
            {relation.description && <p className="relation-description">{relation.description}</p>}
            {relation.sourceEvidence.length > 0 && <p className="relation-evidence">{relation.sourceEvidence.map((evidence) => (
              <button key={evidence.chunkId} className="citation-link" type="button" onClick={() => onOpenEvidence(evidence)}>
                {evidence.sourceTitle} · {evidence.heading || 'Documento'}
              </button>
            ))}</p>}
          </div>
        })}
      </section>
      <footer className="entity-document-footer">
        <span>{promotionLabel(entity.promotionHistory.length)}</span>
        {canEdit && <div className="entity-actions">
          <button className="citation-link" disabled={saving} type="button" onClick={() => onEdit(entity)}>Editar</button>
          {entity.canonStatus === 'PROPOSED' && <button className="citation-link" disabled={saving} type="button" onClick={() => void onPromote(entity)}>Promover a canon</button>}
          <DangerButton disabled={saving} onClick={() => void onDelete(entity)}>Retirar</DangerButton>
        </div>}
      </footer>
    </article>
    <aside className="entity-context" aria-label="Fuentes de la ficha">
      <h3>Fuentes vinculadas</h3>
      {firstEvidence ? <>
        <CatalogueEvidence evidence={[firstEvidence]} onOpen={onOpenEvidence} mark={visibility} />
        <EvidencePreview key={`${entity.realmId}:${firstEvidence.chunkId}:${firstEvidence.documentVersionId}:${firstEvidence.startOffset}:${firstEvidence.endOffset}`} contentApi={contentApi} realmId={entity.realmId} evidence={firstEvidence} />
        {otherEvidence.length > 0 && <CatalogueEvidence evidence={otherEvidence} onOpen={onOpenEvidence} mark={visibility} />}
      </> : <CatalogueEvidence evidence={[]} onOpen={onOpenEvidence} />}
      <p className="context-note">{canEdit ? 'Una afirmación solo cita fuentes con su misma visibilidad. ' : ''}Abre una fuente para contrastar el fragmento vinculado.</p>
    </aside>
  </div>
}
