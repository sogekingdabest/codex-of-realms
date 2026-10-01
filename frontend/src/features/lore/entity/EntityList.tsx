import { Link } from '../../../shared/routing'
import { entityTypeLabels } from '../catalogueModel'
import type { LoreEntityView } from '../model'

export function EntityList({ entities, loading, selectedId, entityHref }: Readonly<{
  entities: LoreEntityView[]
  loading: boolean
  selectedId?: string
  entityHref: (id: string) => string
}>) {
  if (loading) return <p className="index-message" role="status">Abriendo el atlas…</p>
  if (entities.length === 0) return <p className="index-message" role="status">No hay fichas visibles con estos filtros.</p>
  return <nav className="entity-index" aria-label="Fichas del mundo">
    {Object.entries(entityTypeLabels).map(([type, label]) => {
      const group = entities.filter((entity) => entity.type === type)
      if (group.length === 0) return null
      return <div className="entity-index-group" key={type}>
        <h3>{label}<span>{group.length}</span></h3>
        {group.map((entity) => <Link key={entity.id} href={entityHref(entity.id)}
          aria-current={selectedId === entity.id ? 'page' : undefined}
          aria-label={`Abrir ficha de ${entity.displayName}`}>
          <span>{entity.displayName}</span>
          {entity.canonStatus === 'PROPOSED' && <small>Propuesto</small>}
        </Link>)}
      </div>
    })}
  </nav>
}
