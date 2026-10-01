import type { VisibilityAudience } from '../../../shared/lib/visibility'
import { Link } from '../../../shared/routing'
import { VisibilityMark } from '../../../shared/ui/VisibilityMark'
import { CanonMark } from '../CanonSeal'
import { entityTypeLabels } from '../catalogueModel'
import { EntityTypeIcon } from '../EntityTypeIcon'
import type { EntityType, LoreEntityView } from '../model'

export function EntityList({ entities, loading, selectedId, audience, entityHref }: Readonly<{
  entities: LoreEntityView[]
  loading: boolean
  selectedId?: string
  audience: VisibilityAudience
  entityHref: (id: string) => string
}>) {
  if (loading) return <p className="index-message" role="status">Abriendo el atlas…</p>
  if (entities.length === 0) return <p className="index-message" role="status">No hay fichas visibles con estos filtros.</p>
  return <nav className="entity-index" aria-label="Fichas del mundo">
    {(Object.entries(entityTypeLabels) as [EntityType, string][]).map(([type, label]) => {
      const group = entities.filter((entity) => entity.type === type)
      if (group.length === 0) return null
      return <div className="entity-index-group" key={type}>
        <h3><EntityTypeIcon type={type} /><span className="entity-index-label">{label}</span><span>{group.length}</span></h3>
        {group.map((entity) => <Link key={entity.id} href={entityHref(entity.id)}
          aria-current={selectedId === entity.id ? 'page' : undefined}
          aria-label={`Abrir ficha de ${entity.displayName}`}>
          <span className="entity-index-name">{entity.displayName}</span>
          <span className="entity-index-marks">
            <CanonMark status={entity.canonStatus} showLabel={false} />
            <VisibilityMark compact visibility={entity.visibility} viewer={audience.viewer} revealedTo={audience.revealedTo(entity.accessPolicyId)} />
          </span>
        </Link>)}
      </div>
    })}
  </nav>
}
