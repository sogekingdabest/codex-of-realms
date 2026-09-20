import type { SourceEvidence } from '../../content'
import type { AccessPolicyView } from '../../realm'
import { CanonBadge, CatalogueEmpty, CatalogueEvidence } from '../CataloguePrimitives'
import type { LoreRelationView } from '../model'

interface RelationListProps {
  readonly canEdit: boolean
  readonly entityCount: number
  readonly loading: boolean
  readonly policies: AccessPolicyView[]
  readonly relations: LoreRelationView[]
  readonly saving: boolean
  readonly onDelete: (relation: LoreRelationView) => Promise<void>
  readonly onEdit: (relation: LoreRelationView) => void
  readonly onOpenEvidence: (evidence: SourceEvidence) => void
  readonly onOpenEntity: (id: string) => void
  readonly onPromote: (relation: LoreRelationView) => Promise<void>
}

export function RelationList({ canEdit, entityCount, loading, policies, relations, saving, onDelete, onEdit, onOpenEvidence, onOpenEntity, onPromote }: RelationListProps) {
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
          policy={policies.find((policy) => policy.id === relation.accessPolicyId)}
          relation={relation}
          saving={saving}
          onDelete={() => void onDelete(relation)}
          onEdit={() => onEdit(relation)}
          onOpenEvidence={onOpenEvidence}
          onOpenEntity={onOpenEntity}
          onPromote={() => void onPromote(relation)}
        />
      ))}
    </div>
  )
}

function RelationCard({ relation, policy, canEdit, saving, onEdit, onPromote, onDelete, onOpenEvidence, onOpenEntity }: Readonly<{
  relation: LoreRelationView
  policy?: AccessPolicyView
  canEdit: boolean
  saving: boolean
  onEdit: () => void
  onPromote: () => void
  onDelete: () => void
  onOpenEvidence: (evidence: SourceEvidence) => void
  onOpenEntity: (id: string) => void
}>) {
  return (
    <article className="catalogue-card relation-card">
      <header>
        <div className="relation-title">
          <button className="citation-link" type="button" aria-label={`Ver ficha de ${relation.sourceEntityName}`} onClick={() => onOpenEntity(relation.sourceEntityId)}>{relation.sourceEntityName}</button>
          <span>{relation.relationType.replaceAll('_', ' ').toLocaleLowerCase('es')}</span>
          <button className="citation-link" type="button" aria-label={`Ver ficha de ${relation.targetEntityName}`} onClick={() => onOpenEntity(relation.targetEntityId)}>{relation.targetEntityName}</button>
        </div>
        <CanonBadge status={relation.canonStatus} />
      </header>
      <p>{relation.description}</p>
      <CatalogueEvidence evidence={relation.sourceEvidence} onOpen={onOpenEvidence} />
      <footer>
        <span>{policy?.name ?? 'Visibilidad autorizada'} · {relation.promotionHistory.length} promociones</span>
        {canEdit && (
          <div>
            <button className="citation-link" disabled={saving} type="button" onClick={onEdit}>Editar</button>
            {relation.canonStatus === 'PROPOSED' && <button className="citation-link" disabled={saving} type="button" onClick={onPromote}>Promover a canon</button>}
            <button className="text-danger" disabled={saving} type="button" onClick={onDelete}>Retirar</button>
          </div>
        )}
      </footer>
    </article>
  )
}
