import { useState, type ReactNode } from 'react'
import type { VisibilityAudience } from '../../shared/lib/visibility'
import { Link, navigate } from '../../shared/routing'
import type { ContentApi, SourceDocumentView, SourceEvidence } from '../content'
import type { AccessPolicyView } from '../realm'
import type { LoreApi } from './api'
import { entityTypeLabels } from './catalogueModel'
import { EntityEditor } from './entity/EntityEditor'
import { EntityList } from './entity/EntityList'
import { EntityDocument } from './entity/EntityDocument'
import { CatalogueEmpty } from './CataloguePrimitives'
import type { CanonStatus, CatalogueLocation, EntityType } from './model'
import { RelationEditor } from './relation/RelationEditor'
import { RelationList } from './relation/RelationList'
import { useCatalogueWorkspace } from './useCatalogueWorkspace'

/** The catalogue fills two zones of the workspace; the app decides where they go. */
export interface CatalogueSlots {
  readonly index: ReactNode
  readonly content: ReactNode
  /** Changes whenever a different ficha, relation list or editor takes the reading zone. */
  readonly selection: string | null
}

interface CatalogueWorkspaceProps {
  readonly contentApi: ContentApi
  readonly loreApi: LoreApi
  readonly realmId: string
  readonly canEdit: boolean
  readonly policies: AccessPolicyView[]
  readonly audience: VisibilityAudience
  readonly sources: SourceDocumentView[]
  readonly location: CatalogueLocation
  readonly hrefFor: (location: CatalogueLocation) => string
  readonly onOpenEvidence: (evidence: SourceEvidence) => void
  readonly layout: (slots: CatalogueSlots) => ReactNode
}

export function CatalogueWorkspace({
  contentApi,
  loreApi,
  realmId,
  canEdit,
  policies,
  audience,
  sources,
  location,
  hrefFor,
  onOpenEvidence,
  layout,
}: CatalogueWorkspaceProps) {
  const catalogue = useCatalogueWorkspace({
    loreApi, policies, realmId, location, onNavigate: (next) => navigate(hrefFor(next)),
  })
  const [creating, setCreating] = useState<'entity' | 'relation' | null>(null)
  const entityHref = (entityId: string) => hrefFor({ view: 'entities', entityId })
  const showEntityEditor = canEdit && (creating === 'entity' || Boolean(catalogue.editingEntityId))
  const showRelationEditor = canEdit && (creating === 'relation' || Boolean(catalogue.editingRelationId))
  function closeEditors() {
    setCreating(null)
    catalogue.cancelEntityEdit()
    catalogue.cancelRelationEdit()
  }

  // Following a link or going back in history leaves any open editor.
  const locationKey = `${location.view}/${location.entityId ?? ''}`
  const [editorsLocation, setEditorsLocation] = useState(locationKey)
  if (editorsLocation !== locationKey) {
    setEditorsLocation(locationKey)
    closeEditors()
  }

  const index = <div className="catalogue-index">
    <h2>Atlas del canon</h2>
    <CatalogueToolbar
        canonFilter={catalogue.canonFilter}
        search={catalogue.search}
        view={location.view}
        typeFilter={catalogue.typeFilter}
        hrefFor={hrefFor}
        onCanonFilterChange={(filter) => { closeEditors(); catalogue.setCanonFilter(filter) }}
        onSearchChange={(search) => { closeEditors(); catalogue.setSearch(search) }}
        onTypeFilterChange={(filter) => { closeEditors(); catalogue.setTypeFilter(filter) }}
      />
    {location.view === 'entities' && <EntityList entities={catalogue.visibleEntities} loading={catalogue.loading}
      selectedId={catalogue.selectedEntity?.id} audience={audience} entityHref={entityHref} />}
    {canEdit && <div className="index-actions"><button className="quiet-button" type="button" disabled={catalogue.loading || catalogue.saving || (location.view === 'relations' && catalogue.entities.length < 2)}
      onClick={() => { closeEditors(); setCreating(location.view === 'entities' ? 'entity' : 'relation') }}>{location.view === 'entities' ? 'Nueva ficha' : 'Nueva relación'}</button></div>}
  </div>

  const content = <section className="catalogue-content" aria-busy={catalogue.loading}>
    {catalogue.error && <div className="catalogue-error" role="alert">{catalogue.error}</div>}
    {location.view === 'entities' ? (
      showEntityEditor ? <div className="editor-pane">
        <EntityEditor
          contentApi={contentApi}
          draft={catalogue.effectiveEntityDraft}
          editing={Boolean(catalogue.editingEntityId)}
          policies={policies}
          realmId={realmId}
          saving={catalogue.saving}
          sources={sources}
          onCancel={closeEditors}
          onChange={catalogue.setEntityDraft}
          onSubmit={async (event) => { if (await catalogue.saveEntity(event)) setCreating(null) }}
        />
      </div> : catalogue.selectedEntity ? <EntityDocument
        contentApi={contentApi}
        canEdit={canEdit}
        entity={catalogue.selectedEntity}
        relations={catalogue.relations}
        audience={audience}
        saving={catalogue.saving}
        focusHeading={location.entityId !== null}
        entityHref={entityHref}
        onDelete={catalogue.deleteEntity}
        onEdit={catalogue.editEntity}
        onOpenEvidence={onOpenEvidence}
        onPromote={catalogue.promoteEntity}
      /> : <CatalogueEmpty text={catalogue.loading ? 'Abriendo el atlas…'
        : catalogue.entityMissing ? 'Esta ficha no existe o no es visible para ti.'
          : 'No hay fichas visibles con estos filtros.'} />
    ) : (
      <div className="relations-workspace">
        {showRelationEditor ? <div className="editor-pane">
          <RelationEditor
            contentApi={contentApi}
            draft={catalogue.effectiveRelationDraft}
            editing={Boolean(catalogue.editingRelationId)}
            entities={catalogue.entities}
            policies={policies}
            realmId={realmId}
            saving={catalogue.saving}
            sources={sources}
            onCancel={closeEditors}
            onChange={catalogue.setRelationDraft}
            onSubmit={async (event) => { if (await catalogue.saveRelation(event)) setCreating(null) }}
          />
        </div> : <><header className="relations-heading"><h2>Relaciones del mundo</h2><p>Vínculos entre las fichas visibles de este universo.</p></header><RelationList
          canEdit={canEdit}
          entityCount={catalogue.entities.length}
          loading={catalogue.loading}
          audience={audience}
          relations={catalogue.visibleRelations}
          saving={catalogue.saving}
          entityHref={entityHref}
          onDelete={catalogue.deleteRelation}
          onEdit={catalogue.editRelation}
          onOpenEvidence={onOpenEvidence}
          onPromote={catalogue.promoteRelation}
        /></>}
      </div>
    )}
  </section>

  const editing = creating ?? (catalogue.editingEntityId || catalogue.editingRelationId ? 'editor' : null)
  return layout({ index, content, selection: editing ?? location.entityId })
}

interface CatalogueToolbarProps {
  readonly canonFilter: CanonStatus | 'ALL'
  readonly search: string
  readonly view: CatalogueLocation['view']
  readonly typeFilter: EntityType | 'ALL'
  readonly hrefFor: (location: CatalogueLocation) => string
  readonly onCanonFilterChange: (filter: CanonStatus | 'ALL') => void
  readonly onSearchChange: (search: string) => void
  readonly onTypeFilterChange: (filter: EntityType | 'ALL') => void
}

function CatalogueToolbar({ canonFilter, search, view, typeFilter, hrefFor, onCanonFilterChange, onSearchChange, onTypeFilterChange }: CatalogueToolbarProps) {
  return (
    <div className="catalogue-toolbar">
      <nav className="segmented-control" aria-label="Sección del catálogo">
        <Link className={view === 'entities' ? 'active' : undefined} aria-current={view === 'entities' ? 'page' : undefined}
          href={hrefFor({ view: 'entities', entityId: null })}>Fichas</Link>
        <Link className={view === 'relations' ? 'active' : undefined} aria-current={view === 'relations' ? 'page' : undefined}
          href={hrefFor({ view: 'relations', entityId: null })}>Relaciones</Link>
      </nav>
      <label>
        <span>Buscar</span>
        <input
          aria-label="Buscar en el canon"
          value={search}
          onChange={(event) => onSearchChange(event.target.value)}
          placeholder="Nombre, alias o relación"
        />
      </label>
      <details className="catalogue-filters"><summary>Filtrar fichas y relaciones</summary>
      {view === 'entities' && (
        <label>
          <span>Tipo</span>
          <select value={typeFilter} onChange={(event) => onTypeFilterChange(event.target.value as EntityType | 'ALL')}>
            <option value="ALL">Todos</option>
            {Object.entries(entityTypeLabels).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </label>
      )}
      <label>
        <span>Estado</span>
        <select value={canonFilter} onChange={(event) => onCanonFilterChange(event.target.value as CanonStatus | 'ALL')}>
          <option value="ALL">Todos</option>
          <option value="CANON">Canon</option>
          <option value="PROPOSED">Propuesto</option>
        </select>
      </label>
      </details>
    </div>
  )
}
