import type { VisibilityAudience } from '../../../shared/lib/visibility'
import { Link } from '../../../shared/routing'
import { DangerButton } from '../../../shared/ui/DangerButton'
import { VisibilityMark } from '../../../shared/ui/VisibilityMark'
import type { SourceEvidence } from '../../content'
import { CatalogueEmpty, CatalogueEvidence } from '../CataloguePrimitives'
import { CanonMark } from '../CanonSeal'
import { promotionLabel, relationVerb } from '../catalogueModel'
import type { LoreRelationView } from '../model'

interface RelationListProps {
  readonly canEdit: boolean
  readonly entityCount: number
  readonly loading: boolean
  readonly audience: VisibilityAudience
  readonly relations: LoreRelationView[]
  readonly saving: boolean
  readonly onDelete: (relation: LoreRelationView) => Promise<void>
  readonly onEdit: (relation: LoreRelationView) => void
  readonly onOpenEvidence: (evidence: SourceEvidence) => void
  readonly entityHref: (id: string) => string
  readonly onPromote: (relation: LoreRelationView) => Promise<void>
}

export function RelationList({ canEdit, entityCount, loading, audience, relations, saving, onDelete, onEdit, onOpenEvidence, entityHref, onPromote }: RelationListProps) {
  if (loading) return <div className="catalogue-list"><p className="muted">Trazando relaciones…</p></div>
  return (
    <div className="catalogue-list">
      {canEdit && entityCount < 2 && (
        <CatalogueEmpty text="Crea al menos dos fichas para poder relacionarlas." />
      )}
      {relations.length === 0 ? (
        <CatalogueEmpty text="No hay relaciones visibles con estos filtros." />
      ) : relations.map((relation) => (
        <RelationCard
          canEdit={canEdit}
          key={relation.id}
          audience={audience}
          relation={relation}
          saving={saving}
          onDelete={() => void onDelete(relation)}
          onEdit={() => onEdit(relation)}
          onOpenEvidence={onOpenEvidence}
          entityHref={entityHref}
          onPromote={() => void onPromote(relation)}
        />
      ))}
    </div>
  )
}

function RelationCard({ relation, audience, canEdit, saving, onEdit, onPromote, onDelete, onOpenEvidence, entityHref }: Readonly<{
  relation: LoreRelationView
  audience: VisibilityAudience
  canEdit: boolean
  saving: boolean
  onEdit: () => void
  onPromote: () => void
  onDelete: () => void
  onOpenEvidence: (evidence: SourceEvidence) => void
  entityHref: (id: string) => string
}>) {
  const visibility = <VisibilityMark visibility={relation.visibility} viewer={audience.viewer} revealedTo={audience.revealedTo(relation.accessPolicyId)} />
  return (
    <article className="catalogue-card relation-card">
      <header>
        <p className="relation-sentence">
          <Link className="relation-entity" aria-label={`Ver ficha de ${relation.sourceEntityName}`} href={entityHref(relation.sourceEntityId)}>{relation.sourceEntityName}</Link>
          {' '}<span className="relation-verb">{relationVerb(relation.relationType)}</span>{' '}
          <Link className="relation-entity" aria-label={`Ver ficha de ${relation.targetEntityName}`} href={entityHref(relation.targetEntityId)}>{relation.targetEntityName}</Link>
        </p>
        <span className="relation-marks"><CanonMark status={relation.canonStatus} />{visibility}</span>
      </header>
      {relation.description && <p>{relation.description}</p>}
      <CatalogueEvidence evidence={relation.sourceEvidence} onOpen={onOpenEvidence} />
      <footer>
        <span>{promotionLabel(relation.promotionHistory.length)}</span>
        {canEdit && (
          <div>
            <button className="citation-link" disabled={saving} type="button" onClick={onEdit}>Editar</button>
            {relation.canonStatus === 'PROPOSED' && <button className="citation-link" disabled={saving} type="button" onClick={onPromote}>Promover a canon</button>}
            <DangerButton disabled={saving} onClick={onDelete}>Retirar</DangerButton>
          </div>
        )}
      </footer>
    </article>
  )
}
