import { useEffect, useMemo, useState, type SubmitEvent } from 'react'

import type { CodexApi } from './api'
import type {
  AccessPolicyView,
  CanonStatus,
  EntityType,
  LoreEntityInput,
  LoreEntityView,
  LoreRelationInput,
  LoreRelationView,
  SourceDocumentView,
  SourceEvidence,
} from './types'

interface CatalogueWorkspaceProps {
  readonly api: CodexApi
  readonly realmId: string
  readonly canEdit: boolean
  readonly policies: AccessPolicyView[]
  readonly sources: SourceDocumentView[]
  readonly onOpenEvidence: (evidence: SourceEvidence) => void
}

const entityTypeLabels: Record<EntityType, string> = {
  CHARACTER: 'Personaje',
  PLACE: 'Lugar',
  FACTION: 'Facción',
  OBJECT: 'Objeto',
  EVENT: 'Evento',
}

const emptyEntity = (policyId = ''): LoreEntityInput => ({
  type: 'CHARACTER',
  displayName: '',
  aliases: [],
  description: '',
  accessPolicyId: policyId,
  evidenceChunkIds: [],
})

const emptyRelation = (
  policyId = '',
  sourceEntityId = '',
  targetEntityId = '',
): LoreRelationInput => ({
  sourceEntityId,
  targetEntityId,
  relationType: '',
  description: '',
  accessPolicyId: policyId,
  evidenceChunkIds: [],
})

export function CatalogueWorkspace({
  api,
  realmId,
  canEdit,
  policies,
  sources,
  onOpenEvidence,
}: CatalogueWorkspaceProps) {
  const [entities, setEntities] = useState<LoreEntityView[]>([])
  const [relations, setRelations] = useState<LoreRelationView[]>([])
  const [section, setSection] = useState<'entities' | 'relations'>('entities')
  const [search, setSearch] = useState('')
  const [typeFilter, setTypeFilter] = useState<EntityType | 'ALL'>('ALL')
  const [canonFilter, setCanonFilter] = useState<CanonStatus | 'ALL'>('ALL')
  const [entityDraft, setEntityDraft] = useState<LoreEntityInput>(() => emptyEntity(policies[0]?.id))
  const [relationDraft, setRelationDraft] = useState<LoreRelationInput>(() => emptyRelation(policies[0]?.id))
  const [editingEntityId, setEditingEntityId] = useState<string | null>(null)
  const [editingRelationId, setEditingRelationId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    Promise.all([api.listLoreEntities(realmId), api.listLoreRelations(realmId)])
      .then(([nextEntities, nextRelations]) => {
        if (!active) return
        setEntities(nextEntities)
        setRelations(nextRelations)
      })
      .catch((reason: unknown) => {
        if (active) setError(errorMessage(reason))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [api, realmId])

  const effectiveEntityDraft = {
    ...entityDraft,
    accessPolicyId: entityDraft.accessPolicyId || policies[0]?.id || '',
  }
  const relationSourceId = relationDraft.sourceEntityId || entities[0]?.id || ''
  const effectiveRelationDraft = {
    ...relationDraft,
    sourceEntityId: relationSourceId,
    targetEntityId: relationDraft.targetEntityId
      || entities.find((entity) => entity.id !== relationSourceId)?.id
      || '',
    accessPolicyId: relationDraft.accessPolicyId || policies[0]?.id || '',
  }

  const normalizedSearch = search.trim().toLocaleLowerCase('es')
  const visibleEntities = useMemo(() => entities.filter((entity) => {
    if (typeFilter !== 'ALL' && entity.type !== typeFilter) return false
    if (canonFilter !== 'ALL' && entity.canonStatus !== canonFilter) return false
    if (!normalizedSearch) return true
    return [entity.displayName, entity.description, ...entity.aliases]
      .some((value) => value.toLocaleLowerCase('es').includes(normalizedSearch))
  }), [canonFilter, entities, normalizedSearch, typeFilter])

  const visibleRelations = useMemo(() => relations.filter((relation) => {
    if (canonFilter !== 'ALL' && relation.canonStatus !== canonFilter) return false
    if (!normalizedSearch) return true
    return [
      relation.sourceEntityName,
      relation.targetEntityName,
      relation.relationType,
      relation.description,
    ].some((value) => value.toLocaleLowerCase('es').includes(normalizedSearch))
  }), [canonFilter, normalizedSearch, relations])

  async function saveEntity(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!effectiveEntityDraft.accessPolicyId) return
    setSaving(true)
    setError(null)
    try {
      const saved = editingEntityId
        ? await api.updateLoreEntity(realmId, editingEntityId, effectiveEntityDraft)
        : await api.createLoreEntity(realmId, effectiveEntityDraft)
      setEntities((current) => upsert(current, saved))
      setEditingEntityId(null)
      setEntityDraft(emptyEntity(policies[0]?.id))
    } catch (reason) {
      setError(errorMessage(reason))
    } finally {
      setSaving(false)
    }
  }

  async function saveRelation(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!effectiveRelationDraft.accessPolicyId || !effectiveRelationDraft.sourceEntityId || !effectiveRelationDraft.targetEntityId) return
    setSaving(true)
    setError(null)
    try {
      const saved = editingRelationId
        ? await api.updateLoreRelation(realmId, editingRelationId, {
            relationType: relationDraft.relationType,
            description: relationDraft.description,
            accessPolicyId: effectiveRelationDraft.accessPolicyId,
            evidenceChunkIds: relationDraft.evidenceChunkIds,
          })
        : await api.createLoreRelation(realmId, effectiveRelationDraft)
      setRelations((current) => upsert(current, saved))
      setEditingRelationId(null)
      setRelationDraft(emptyRelation(
        policies[0]?.id,
        entities[0]?.id,
        entities[1]?.id,
      ))
    } catch (reason) {
      setError(errorMessage(reason))
    } finally {
      setSaving(false)
    }
  }

  async function promoteEntity(entity: LoreEntityView) {
    setSaving(true)
    setError(null)
    try {
      const promoted = await api.promoteLoreEntity(realmId, entity.id)
      setEntities((current) => upsert(current, promoted))
    } catch (reason) {
      setError(errorMessage(reason))
    } finally {
      setSaving(false)
    }
  }

  async function promoteRelation(relation: LoreRelationView) {
    setSaving(true)
    setError(null)
    try {
      const promoted = await api.promoteLoreRelation(realmId, relation.id)
      setRelations((current) => upsert(current, promoted))
    } catch (reason) {
      setError(errorMessage(reason))
    } finally {
      setSaving(false)
    }
  }

  async function deleteEntity(entity: LoreEntityView) {
    if (!window.confirm(`¿Retirar «${entity.displayName}» del catálogo?`)) return
    setSaving(true)
    setError(null)
    try {
      await api.deleteLoreEntity(realmId, entity.id)
      setEntities((current) => current.filter((item) => item.id !== entity.id))
    } catch (reason) {
      setError(errorMessage(reason))
    } finally {
      setSaving(false)
    }
  }

  async function deleteRelation(relation: LoreRelationView) {
    if (!window.confirm(`¿Retirar la relación «${relation.relationType}»?`)) return
    setSaving(true)
    setError(null)
    try {
      await api.deleteLoreRelation(realmId, relation.id)
      setRelations((current) => current.filter((item) => item.id !== relation.id))
    } catch (reason) {
      setError(errorMessage(reason))
    } finally {
      setSaving(false)
    }
  }

  function editEntity(entity: LoreEntityView) {
    setSection('entities')
    setEditingEntityId(entity.id)
    setEntityDraft({
      type: entity.type,
      displayName: entity.displayName,
      aliases: entity.aliases,
      description: entity.description,
      accessPolicyId: entity.accessPolicyId,
      evidenceChunkIds: entity.sourceEvidence.map((evidence) => evidence.chunkId),
    })
  }

  function editRelation(relation: LoreRelationView) {
    setSection('relations')
    setEditingRelationId(relation.id)
    setRelationDraft({
      sourceEntityId: relation.sourceEntityId,
      targetEntityId: relation.targetEntityId,
      relationType: relation.relationType,
      description: relation.description,
      accessPolicyId: relation.accessPolicyId,
      evidenceChunkIds: relation.sourceEvidence.map((evidence) => evidence.chunkId),
    })
  }

  return (
    <section className="catalogue-workspace" aria-busy={loading}>
      <header className="catalogue-header">
        <div>
          <p className="eyebrow">Conocimiento estructurado · promoción humana</p>
          <h2>Atlas del canon</h2>
          <p>Registra conceptos y vínculos sin permitir que el modelo decida qué es verdad.</p>
        </div>
        <div className="catalogue-summary" aria-label="Resumen del catálogo">
          <span><strong>{entities.length}</strong> fichas</span>
          <span><strong>{relations.length}</strong> relaciones</span>
          <span><strong>{entities.filter((entity) => entity.canonStatus === 'CANON').length}</strong> canónicas</span>
        </div>
      </header>

      <div className="catalogue-toolbar">
        <div className="segmented-control" aria-label="Sección del catálogo">
          <button
            className={section === 'entities' ? 'active' : ''}
            type="button"
            onClick={() => setSection('entities')}
          >
            Fichas
          </button>
          <button
            className={section === 'relations' ? 'active' : ''}
            type="button"
            onClick={() => setSection('relations')}
          >
            Relaciones
          </button>
        </div>
        <label>
          <span>Buscar</span>
          <input
            aria-label="Buscar en el canon"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Nombre, alias o relación"
          />
        </label>
        {section === 'entities' && (
          <label>
            <span>Tipo</span>
            <select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value as EntityType | 'ALL')}>
              <option value="ALL">Todos</option>
              {Object.entries(entityTypeLabels).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </label>
        )}
        <label>
          <span>Estado</span>
          <select value={canonFilter} onChange={(event) => setCanonFilter(event.target.value as CanonStatus | 'ALL')}>
            <option value="ALL">Todos</option>
            <option value="CANON">Canon</option>
            <option value="PROPOSED">Propuesto</option>
          </select>
        </label>
      </div>

      {error && <div className="catalogue-error" role="alert">{error}</div>}

      {section === 'entities' ? (
        <div className="catalogue-layout">
          {canEdit && (
            <EntityForm
              api={api}
              draft={effectiveEntityDraft}
              editing={Boolean(editingEntityId)}
              policies={policies}
              realmId={realmId}
              saving={saving}
              sources={sources}
              onCancel={() => {
                setEditingEntityId(null)
                setEntityDraft(emptyEntity(policies[0]?.id))
              }}
              onChange={setEntityDraft}
              onSubmit={saveEntity}
            />
          )}
          <EntityList
            canEdit={canEdit}
            entities={visibleEntities}
            loading={loading}
            policies={policies}
            saving={saving}
            onDelete={deleteEntity}
            onEdit={editEntity}
            onOpenEvidence={onOpenEvidence}
            onPromote={promoteEntity}
          />
        </div>
      ) : (
        <div className="catalogue-layout">
          {canEdit && entities.length >= 2 && (
            <RelationForm
              api={api}
              draft={effectiveRelationDraft}
              editing={Boolean(editingRelationId)}
              entities={entities}
              policies={policies}
              realmId={realmId}
              saving={saving}
              sources={sources}
              onCancel={() => {
                setEditingRelationId(null)
                setRelationDraft(emptyRelation(policies[0]?.id, entities[0]?.id, entities[1]?.id))
              }}
              onChange={setRelationDraft}
              onSubmit={saveRelation}
            />
          )}
          <RelationList
            canEdit={canEdit}
            entityCount={entities.length}
            loading={loading}
            policies={policies}
            relations={visibleRelations}
            saving={saving}
            onDelete={deleteRelation}
            onEdit={editRelation}
            onOpenEvidence={onOpenEvidence}
            onPromote={promoteRelation}
          />
        </div>
      )}
    </section>
  )
}

interface EntityFormProps {
  readonly api: CodexApi
  readonly draft: LoreEntityInput
  readonly editing: boolean
  readonly policies: AccessPolicyView[]
  readonly realmId: string
  readonly saving: boolean
  readonly sources: SourceDocumentView[]
  readonly onCancel: () => void
  readonly onChange: (draft: LoreEntityInput) => void
  readonly onSubmit: (event: SubmitEvent<HTMLFormElement>) => void
}

function EntityForm({ api, draft, editing, policies, realmId, saving, sources, onCancel, onChange, onSubmit }: EntityFormProps) {
  return (
    <form className="catalogue-editor" onSubmit={onSubmit}>
      <div className="catalogue-editor-heading">
        <div>
          <p className="eyebrow">{editing ? 'Revisión manual' : 'Nueva ficha'}</p>
          <h3>{editing ? 'Editar concepto' : 'Registrar concepto'}</h3>
        </div>
        {editing && <button className="quiet-button" type="button" onClick={onCancel}>Cancelar</button>}
      </div>
      <label>
        <span>Tipo</span>
        <select value={draft.type} onChange={(event) => onChange({ ...draft, type: event.target.value as EntityType })}>
          {Object.entries(entityTypeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
      <label>
        <span>Nombre</span>
        <input required maxLength={160} value={draft.displayName} onChange={(event) => onChange({ ...draft, displayName: event.target.value })} />
      </label>
      <label>
        <span>Alias separados por comas</span>
        <input
          maxLength={1200}
          value={draft.aliases.join(', ')}
          onChange={(event) => onChange({
            ...draft,
            aliases: event.target.value.split(',').map((alias) => alias.trim()).filter(Boolean),
          })}
        />
      </label>
      <label>
        <span>Descripción</span>
        <textarea required rows={5} maxLength={4000} value={draft.description} onChange={(event) => onChange({ ...draft, description: event.target.value })} />
      </label>
      <PolicySelect policies={policies} value={draft.accessPolicyId} onChange={(accessPolicyId) => onChange({ ...draft, accessPolicyId, evidenceChunkIds: [] })} />
      <EvidencePicker
        api={api}
        evidenceChunkIds={draft.evidenceChunkIds}
        policyId={draft.accessPolicyId}
        realmId={realmId}
        sources={sources}
        onChange={(evidenceChunkIds) => onChange({ ...draft, evidenceChunkIds })}
      />
      <button disabled={saving || !draft.accessPolicyId} type="submit">
        {editorSubmitLabel(saving, editing)}
      </button>
    </form>
  )
}

interface RelationFormProps {
  readonly api: CodexApi
  readonly draft: LoreRelationInput
  readonly editing: boolean
  readonly entities: LoreEntityView[]
  readonly policies: AccessPolicyView[]
  readonly realmId: string
  readonly saving: boolean
  readonly sources: SourceDocumentView[]
  readonly onCancel: () => void
  readonly onChange: (draft: LoreRelationInput) => void
  readonly onSubmit: (event: SubmitEvent<HTMLFormElement>) => void
}

function RelationForm({ api, draft, editing, entities, policies, realmId, saving, sources, onCancel, onChange, onSubmit }: RelationFormProps) {
  return (
    <form className="catalogue-editor" onSubmit={onSubmit}>
      <div className="catalogue-editor-heading">
        <div>
          <p className="eyebrow">{editing ? 'Revisión manual' : 'Nuevo vínculo'}</p>
          <h3>{editing ? 'Editar relación' : 'Relacionar conceptos'}</h3>
        </div>
        {editing && <button className="quiet-button" type="button" onClick={onCancel}>Cancelar</button>}
      </div>
      <label>
        <span>Origen</span>
        <select disabled={editing} value={draft.sourceEntityId} onChange={(event) => onChange({ ...draft, sourceEntityId: event.target.value })}>
          {entities.map((entity) => <option key={entity.id} value={entity.id}>{entity.displayName}</option>)}
        </select>
      </label>
      <label>
        <span>Destino</span>
        <select disabled={editing} value={draft.targetEntityId} onChange={(event) => onChange({ ...draft, targetEntityId: event.target.value })}>
          {entities.filter((entity) => entity.id !== draft.sourceEntityId).map((entity) => (
            <option key={entity.id} value={entity.id}>{entity.displayName}</option>
          ))}
        </select>
      </label>
      <label>
        <span>Tipo de relación</span>
        <input required maxLength={64} value={draft.relationType} onChange={(event) => onChange({ ...draft, relationType: event.target.value })} placeholder="Protege, vive en, pertenece a…" />
      </label>
      <label>
        <span>Descripción</span>
        <textarea required rows={4} maxLength={2000} value={draft.description} onChange={(event) => onChange({ ...draft, description: event.target.value })} />
      </label>
      <PolicySelect policies={policies} value={draft.accessPolicyId} onChange={(accessPolicyId) => onChange({ ...draft, accessPolicyId, evidenceChunkIds: [] })} />
      <EvidencePicker
        api={api}
        evidenceChunkIds={draft.evidenceChunkIds}
        policyId={draft.accessPolicyId}
        realmId={realmId}
        sources={sources}
        onChange={(evidenceChunkIds) => onChange({ ...draft, evidenceChunkIds })}
      />
      <button disabled={saving || draft.sourceEntityId === draft.targetEntityId} type="submit">
        {editorSubmitLabel(saving, editing)}
      </button>
    </form>
  )
}

function PolicySelect({ policies, value, onChange }: Readonly<{ policies: AccessPolicyView[]; value: string; onChange: (value: string) => void }>) {
  return (
    <label>
      <span>Visibilidad de la afirmación</span>
      <select required value={value} onChange={(event) => onChange(event.target.value)}>
        {policies.map((policy) => <option key={policy.id} value={policy.id}>{policy.name}</option>)}
      </select>
    </label>
  )
}

interface EvidencePickerProps {
  readonly api: CodexApi
  readonly evidenceChunkIds: string[]
  readonly policyId: string
  readonly realmId: string
  readonly sources: SourceDocumentView[]
  readonly onChange: (ids: string[]) => void
}

function EvidencePicker({ api, evidenceChunkIds, policyId, realmId, sources, onChange }: EvidencePickerProps) {
  const eligibleSources = useMemo(
    () => sources.filter((source) => source.status === 'READY' && source.accessPolicyId === policyId),
    [policyId, sources],
  )
  const [documentId, setDocumentId] = useState(() => eligibleSources[0]?.id ?? '')
  const [chunks, setChunks] = useState<Awaited<ReturnType<CodexApi['listSourceChunks']>>>([])
  const [loadedDocumentId, setLoadedDocumentId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const selectedDocumentId = eligibleSources.some((source) => source.id === documentId)
    ? documentId
    : eligibleSources[0]?.id ?? ''
  const loading = Boolean(selectedDocumentId) && loadedDocumentId !== selectedDocumentId

  useEffect(() => {
    if (!selectedDocumentId) return
    let active = true
    api.listSourceChunks(realmId, selectedDocumentId)
      .then((nextChunks) => {
        if (active) {
          setChunks(nextChunks)
          setLoadedDocumentId(selectedDocumentId)
          setError(null)
        }
      })
      .catch((reason: unknown) => {
        if (active) setError(errorMessage(reason))
      })
    return () => {
      active = false
    }
  }, [api, realmId, selectedDocumentId])

  function toggle(chunkId: string) {
    if (evidenceChunkIds.includes(chunkId)) {
      onChange(evidenceChunkIds.filter((id) => id !== chunkId))
    } else if (evidenceChunkIds.length < 20) {
      onChange([...evidenceChunkIds, chunkId])
    }
  }

  return (
    <fieldset className="evidence-picker">
      <legend>Evidencia de fuente <span>{evidenceChunkIds.length}/20</span></legend>
      {eligibleSources.length === 0 ? (
        <p>No hay fuentes listas con esta misma visibilidad. La ficha puede guardarse sin evidencia.</p>
      ) : (
        <>
          <label>
            <span>Fuente</span>
            <select value={selectedDocumentId} onChange={(event) => setDocumentId(event.target.value)}>
              {eligibleSources.map((source) => <option key={source.id} value={source.id}>{source.title}</option>)}
            </select>
          </label>
          {error && <p className="field-error">{error}</p>}
          <ChunkOptions
            chunks={chunks}
            evidenceChunkIds={evidenceChunkIds}
            loading={loading}
            onToggle={toggle}
          />
        </>
      )}
    </fieldset>
  )
}

function EntityList({ canEdit, entities, loading, policies, saving, onDelete, onEdit, onOpenEvidence, onPromote }: Readonly<{
  canEdit: boolean
  entities: LoreEntityView[]
  loading: boolean
  policies: AccessPolicyView[]
  saving: boolean
  onDelete: (entity: LoreEntityView) => Promise<void>
  onEdit: (entity: LoreEntityView) => void
  onOpenEvidence: (evidence: SourceEvidence) => void
  onPromote: (entity: LoreEntityView) => Promise<void>
}>) {
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

function RelationList({ canEdit, entityCount, loading, policies, relations, saving, onDelete, onEdit, onOpenEvidence, onPromote }: Readonly<{
  canEdit: boolean
  entityCount: number
  loading: boolean
  policies: AccessPolicyView[]
  relations: LoreRelationView[]
  saving: boolean
  onDelete: (relation: LoreRelationView) => Promise<void>
  onEdit: (relation: LoreRelationView) => void
  onOpenEvidence: (evidence: SourceEvidence) => void
  onPromote: (relation: LoreRelationView) => Promise<void>
}>) {
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
          onPromote={() => void onPromote(relation)}
        />
      ))}
    </div>
  )
}

function ChunkOptions({ chunks, evidenceChunkIds, loading, onToggle }: Readonly<{
  chunks: Awaited<ReturnType<CodexApi['listSourceChunks']>>
  evidenceChunkIds: string[]
  loading: boolean
  onToggle: (chunkId: string) => void
}>) {
  if (loading) return <div className="chunk-options" aria-live="polite"><p>Abriendo fragmentos…</p></div>
  return (
    <div className="chunk-options" aria-live="polite">
      {chunks.map((chunk) => {
        const label = chunk.heading || `Fragmento ${chunk.ordinal + 1}`
        return (
          <label key={chunk.id}>
            <input
              aria-label={`Seleccionar ${label}`}
              checked={evidenceChunkIds.includes(chunk.id)}
              disabled={!evidenceChunkIds.includes(chunk.id) && evidenceChunkIds.length >= 20}
              type="checkbox"
              onChange={() => onToggle(chunk.id)}
            />
            <span>
              <strong>{label}</strong>
              <small>{excerpt(chunk.content)}</small>
            </span>
          </label>
        )
      })}
    </div>
  )
}

function editorSubmitLabel(saving: boolean, editing: boolean) {
  if (saving) return 'Guardando…'
  return editing ? 'Guardar y devolver a propuesto' : 'Crear como propuesta'
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

function RelationCard({ relation, policy, canEdit, saving, onEdit, onPromote, onDelete, onOpenEvidence }: Readonly<{
  relation: LoreRelationView
  policy?: AccessPolicyView
  canEdit: boolean
  saving: boolean
  onEdit: () => void
  onPromote: () => void
  onDelete: () => void
  onOpenEvidence: (evidence: SourceEvidence) => void
}>) {
  return (
    <article className="catalogue-card relation-card">
      <header>
        <div className="relation-title">
          <strong>{relation.sourceEntityName}</strong>
          <span>{relation.relationType.replaceAll('_', ' ').toLocaleLowerCase('es')}</span>
          <strong>{relation.targetEntityName}</strong>
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

function CatalogueEvidence({ evidence, onOpen }: Readonly<{ evidence: SourceEvidence[]; onOpen: (evidence: SourceEvidence) => void }>) {
  if (evidence.length === 0) return <p className="no-evidence">Afirmación manual sin fuente vinculada.</p>
  return (
    <div className="catalogue-evidence">
      {evidence.map((item) => (
        <button key={item.chunkId} type="button" onClick={() => onOpen(item)}>
          <span>{item.sourceTitle}</span>
          <small>{item.heading || 'Documento'} · {item.startOffset}–{item.endOffset}</small>
        </button>
      ))}
    </div>
  )
}

function CanonBadge({ status }: Readonly<{ status: CanonStatus }>) {
  return <span className={`canon-badge canon-${status.toLocaleLowerCase('es')}`}>{status === 'CANON' ? 'Canon' : 'Propuesto'}</span>
}

function CatalogueEmpty({ text }: Readonly<{ text: string }>) {
  return <div className="catalogue-empty"><span aria-hidden="true">◇</span><p>{text}</p></div>
}

function upsert<T extends { id: string }>(items: T[], value: T) {
  return [value, ...items.filter((item) => item.id !== value.id)]
}

function excerpt(content: string) {
  const normalized = content.replaceAll(/\s+/g, ' ').trim()
  return normalized.length > 180 ? `${normalized.slice(0, 177)}…` : normalized
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Ha ocurrido un error inesperado.'
}
