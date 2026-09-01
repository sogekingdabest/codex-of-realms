import type { ContentApi, SourceDocumentView, SourceEvidence } from '../content'
import type { AccessPolicyView } from '../realm'
import type { LoreApi } from './api'
import { entityTypeLabels } from './catalogueModel'
import { EntityEditor } from './entity/EntityEditor'
import { EntityList } from './entity/EntityList'
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
}

export function CatalogueWorkspace({
  contentApi,
  loreApi,
  realmId,
  canEdit,
  policies,
  sources,
  onOpenEvidence,
}: CatalogueWorkspaceProps) {
  const catalogue = useCatalogueWorkspace({ loreApi, policies, realmId })

  return (
    <section className="catalogue-workspace" aria-busy={catalogue.loading}>
      <header className="catalogue-header">
        <div>
          <p className="eyebrow">Conocimiento estructurado · promoción humana</p>
          <h2>Atlas del canon</h2>
          <p>Registra conceptos y vínculos sin permitir que el modelo decida qué es verdad.</p>
        </div>
        <div className="catalogue-summary" aria-label="Resumen del catálogo">
          <span><strong>{catalogue.entities.length}</strong> fichas</span>
          <span><strong>{catalogue.relations.length}</strong> relaciones</span>
          <span><strong>{catalogue.entities.filter((entity) => entity.canonStatus === 'CANON').length}</strong> canónicas</span>
        </div>
      </header>

      <CatalogueToolbar
        canonFilter={catalogue.canonFilter}
        search={catalogue.search}
        section={catalogue.section}
        typeFilter={catalogue.typeFilter}
        onCanonFilterChange={catalogue.setCanonFilter}
        onSearchChange={catalogue.setSearch}
        onSectionChange={catalogue.setSection}
        onTypeFilterChange={catalogue.setTypeFilter}
      />

      {catalogue.error && <div className="catalogue-error" role="alert">{catalogue.error}</div>}

      {catalogue.section === 'entities' ? (
        <div className="catalogue-layout">
          {canEdit && (
            <EntityEditor
              contentApi={contentApi}
              draft={catalogue.effectiveEntityDraft}
              editing={Boolean(catalogue.editingEntityId)}
              policies={policies}
              realmId={realmId}
              saving={catalogue.saving}
              sources={sources}
              onCancel={catalogue.cancelEntityEdit}
              onChange={catalogue.setEntityDraft}
              onSubmit={catalogue.saveEntity}
            />
          )}
          <EntityList
            canEdit={canEdit}
            entities={catalogue.visibleEntities}
            loading={catalogue.loading}
            policies={policies}
            saving={catalogue.saving}
            onDelete={catalogue.deleteEntity}
            onEdit={catalogue.editEntity}
            onOpenEvidence={onOpenEvidence}
            onPromote={catalogue.promoteEntity}
          />
        </div>
      ) : (
        <div className="catalogue-layout">
          {canEdit && catalogue.entities.length >= 2 && (
            <RelationEditor
              contentApi={contentApi}
              draft={catalogue.effectiveRelationDraft}
              editing={Boolean(catalogue.editingRelationId)}
              entities={catalogue.entities}
              policies={policies}
              realmId={realmId}
              saving={catalogue.saving}
              sources={sources}
              onCancel={catalogue.cancelRelationEdit}
              onChange={catalogue.setRelationDraft}
              onSubmit={catalogue.saveRelation}
            />
          )}
          <RelationList
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
          />
        </div>
      )}
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
          type="button"
          onClick={() => onSectionChange('entities')}
        >
          Fichas
        </button>
        <button
          className={section === 'relations' ? 'active' : ''}
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
    </div>
  )
}
