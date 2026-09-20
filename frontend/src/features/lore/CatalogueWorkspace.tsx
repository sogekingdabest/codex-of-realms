import { useState } from 'react'
import { createPortal } from 'react-dom'
import type { ContentApi, SourceDocumentView, SourceEvidence } from '../content'
import type { AccessPolicyView } from '../realm'
import type { LoreApi } from './api'
import { entityTypeLabels } from './catalogueModel'
import { EntityEditor } from './entity/EntityEditor'
import { EntityList } from './entity/EntityList'
import { EntityDocument } from './entity/EntityDocument'
import { CatalogueEmpty } from './CataloguePrimitives'
import type { CanonStatus, EntityType } from './model'
import { RelationEditor } from './relation/RelationEditor'
import { RelationList } from './relation/RelationList'
import { useCatalogueWorkspace } from './useCatalogueWorkspace'

interface CatalogueWorkspaceProps {
  readonly contentApi: ContentApi
  readonly loreApi: LoreApi
  readonly realmId: string
  readonly canEdit: boolean
  readonly policies: AccessPolicyView[]
  readonly sources: SourceDocumentView[]
  readonly onOpenEvidence: (evidence: SourceEvidence) => void
  readonly indexContainer?: HTMLElement | null
  readonly onSelectEntry?: () => void
}

export function CatalogueWorkspace({
  contentApi,
  loreApi,
  realmId,
  canEdit,
  policies,
  sources,
  onOpenEvidence,
  indexContainer,
  onSelectEntry,
}: CatalogueWorkspaceProps) {
  const catalogue = useCatalogueWorkspace({ loreApi, policies, realmId })
  const [creating, setCreating] = useState<'entity' | 'relation' | null>(null)
  const selectedEntity = catalogue.visibleEntities.find((entity) => entity.id === catalogue.focusedEntityId) ?? catalogue.visibleEntities[0]
  const showEntityEditor = canEdit && (creating === 'entity' || Boolean(catalogue.editingEntityId))
  const showRelationEditor = canEdit && (creating === 'relation' || Boolean(catalogue.editingRelationId))
  function closeEditors() {
    setCreating(null)
    catalogue.cancelEntityEdit()
    catalogue.cancelRelationEdit()
  }
  const index = <div className="catalogue-index">
    <h2>Atlas del canon</h2>
    <CatalogueToolbar
        canonFilter={catalogue.canonFilter}
        search={catalogue.search}
        section={catalogue.section}
        typeFilter={catalogue.typeFilter}
        onCanonFilterChange={(filter) => { closeEditors(); catalogue.setCanonFilter(filter) }}
        onSearchChange={(search) => { closeEditors(); catalogue.setSearch(search) }}
        onSectionChange={(section) => { closeEditors(); catalogue.setSection(section) }}
        onTypeFilterChange={(filter) => { closeEditors(); catalogue.setTypeFilter(filter) }}
      />
    {catalogue.section === 'entities' && <EntityList entities={catalogue.visibleEntities} loading={catalogue.loading} selectedId={selectedEntity?.id}
      onSelect={(id) => { closeEditors(); catalogue.setFocusedEntityId(id); onSelectEntry?.() }} />}
    {canEdit && <div className="index-actions"><button className="quiet-button" type="button" disabled={catalogue.loading || catalogue.saving || (catalogue.section === 'relations' && catalogue.entities.length < 2)}
      onClick={() => { closeEditors(); setCreating(catalogue.section === 'entities' ? 'entity' : 'relation'); onSelectEntry?.() }}>{catalogue.section === 'entities' ? 'Nueva ficha' : 'Nueva relación'}</button></div>}
  </div>

  return (
    <section className={`catalogue-workspace${indexContainer ? '' : ' with-local-index'}`} aria-busy={catalogue.loading}>
      {indexContainer ? createPortal(index, indexContainer) : index}
      <div className="catalogue-content">
        {catalogue.error && <div className="catalogue-error" role="alert">{catalogue.error}</div>}
        {catalogue.section === 'entities' ? (
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
          </div> : selectedEntity ? <EntityDocument
            contentApi={contentApi}
            canEdit={canEdit}
            entity={selectedEntity}
            relations={catalogue.relations}
            policies={policies}
            saving={catalogue.saving}
            focusHeading={Boolean(catalogue.focusedEntityId)}
            onOpenEntity={catalogue.openEntity}
            onDelete={catalogue.deleteEntity}
            onEdit={catalogue.editEntity}
            onOpenEvidence={onOpenEvidence}
            onPromote={catalogue.promoteEntity}
          /> : <CatalogueEmpty text={catalogue.loading ? 'Abriendo el atlas…' : 'No hay fichas visibles con estos filtros.'} />
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
            policies={policies}
            relations={catalogue.visibleRelations}
            saving={catalogue.saving}
            onDelete={catalogue.deleteRelation}
            onEdit={catalogue.editRelation}
            onOpenEvidence={onOpenEvidence}
            onPromote={catalogue.promoteRelation}
            onOpenEntity={catalogue.openEntity}
          /></>}
        </div>
      )}
      </div>
    </section>
  )
}

interface CatalogueToolbarProps {
  readonly canonFilter: CanonStatus | 'ALL'
  readonly search: string
  readonly section: 'entities' | 'relations'
  readonly typeFilter: EntityType | 'ALL'
  readonly onCanonFilterChange: (filter: CanonStatus | 'ALL') => void
  readonly onSearchChange: (search: string) => void
  readonly onSectionChange: (section: 'entities' | 'relations') => void
  readonly onTypeFilterChange: (filter: EntityType | 'ALL') => void
}

function CatalogueToolbar({ canonFilter, search, section, typeFilter, onCanonFilterChange, onSearchChange, onSectionChange, onTypeFilterChange }: CatalogueToolbarProps) {
  return (
    <div className="catalogue-toolbar">
      <div className="segmented-control" aria-label="Sección del catálogo">
        <button
          className={section === 'entities' ? 'active' : ''}
          aria-pressed={section === 'entities'}
          type="button"
          onClick={() => onSectionChange('entities')}
        >
          Fichas
        </button>
        <button
          className={section === 'relations' ? 'active' : ''}
          aria-pressed={section === 'relations'}
          type="button"
          onClick={() => onSectionChange('relations')}
        >
          Relaciones
        </button>
      </div>
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
      {section === 'entities' && (
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
