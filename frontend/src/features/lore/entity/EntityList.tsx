import type { SourceEvidence } from '../../content'
import type { AccessPolicyView } from '../../realm'
import { CanonBadge, CatalogueEmpty, CatalogueEvidence } from '../CataloguePrimitives'
import { entityTypeLabels } from '../catalogueModel'
import type { LoreEntityView } from '../model'

interface EntityListProps {
  readonly canEdit: boolean
  readonly entities: LoreEntityView[]
  readonly loading: boolean
  readonly policies: AccessPolicyView[]
  readonly saving: boolean
  readonly onDelete: (entity: LoreEntityView) => Promise<void>
  readonly onEdit: (entity: LoreEntityView) => void
  readonly onOpenEvidence: (evidence: SourceEvidence) => void
  readonly onPromote: (entity: LoreEntityView) => Promise<void>
}

export function EntityList({ canEdit, entities, loading, policies, saving, onDelete, onEdit, onOpenEvidence, onPromote }: EntityListProps) {
  if (loading) return <div className="catalogue-list"><p className="muted">Abriendo el atlas…</p></div>
  if (entities.length === 0) {
    return <div className="catalogue-list"><CatalogueEmpty text="No hay fichas visibles con estos filtros." /></div>
  }
  return (
    <div className="catalogue-list">
      {entities.map((entity) => (
        <EntityCard
          canEdit={canEdit}
          entity={entity}
          key={entity.id}
          policy={policies.find((policy) => policy.id === entity.accessPolicyId)}
          saving={saving}
          onDelete={() => void onDelete(entity)}
          onEdit={() => onEdit(entity)}
          onOpenEvidence={onOpenEvidence}
          onPromote={() => void onPromote(entity)}
        />
      ))}
    </div>
  )
}

function EntityCard({ entity, policy, canEdit, saving, onEdit, onPromote, onDelete, onOpenEvidence }: Readonly<{
  entity: LoreEntityView
  policy?: AccessPolicyView
  canEdit: boolean
  saving: boolean
  onEdit: () => void
  onPromote: () => void
  onDelete: () => void
  onOpenEvidence: (evidence: SourceEvidence) => void
}>) {
  return (
    <article className="catalogue-card">
      <header>
        <div>
          <span className="entity-type">{entityTypeLabels[entity.type]}</span>
          <h3>{entity.displayName}</h3>
        </div>
        <CanonBadge status={entity.canonStatus} />
      </header>
      {entity.aliases.length > 0 && <p className="aliases">También: {entity.aliases.join(' · ')}</p>}
      <p>{entity.description}</p>
      <CatalogueEvidence evidence={entity.sourceEvidence} onOpen={onOpenEvidence} />
      <footer>
        <span>{policy?.name ?? 'Visibilidad autorizada'} · {entity.promotionHistory.length} promociones</span>
        {canEdit && (
          <div>
            <button className="citation-link" disabled={saving} type="button" onClick={onEdit}>Editar</button>
            {entity.canonStatus === 'PROPOSED' && <button className="citation-link" disabled={saving} type="button" onClick={onPromote}>Promover a canon</button>}
            <button className="text-danger" disabled={saving} type="button" onClick={onDelete}>Retirar</button>
          </div>
        )}
      </footer>
    </article>
  )
}
