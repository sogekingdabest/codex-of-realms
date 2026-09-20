import { entityTypeLabels } from '../catalogueModel'
import type { LoreEntityView } from '../model'

export function EntityList({ entities, loading, selectedId, onSelect }: Readonly<{
  entities: LoreEntityView[]
  loading: boolean
  selectedId?: string
  onSelect: (id: string) => void
}>) {
  if (loading) return <p className="index-message" role="status">Abriendo el atlas…</p>
  if (entities.length === 0) return <p className="index-message" role="status">No hay fichas visibles con estos filtros.</p>
  return <nav className="entity-index" aria-label="Fichas del mundo">
    {Object.entries(entityTypeLabels).map(([type, label]) => {
      const group = entities.filter((entity) => entity.type === type)
      if (group.length === 0) return null
      return <div className="entity-index-group" key={type}>
        <h3>{label}<span>{group.length}</span></h3>
        {group.map((entity) => <button key={entity.id} type="button"
          aria-current={selectedId === entity.id ? 'page' : undefined}
          aria-label={`Abrir ficha de ${entity.displayName}`}
          onClick={() => onSelect(entity.id)}>
          <span>{entity.displayName}</span>
          {entity.canonStatus === 'PROPOSED' && <small>Propuesto</small>}
        </button>)}
      </div>
    })}
  </nav>
}
