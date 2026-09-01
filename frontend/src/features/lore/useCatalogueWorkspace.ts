import { useEffect, useMemo, useState, type SubmitEvent } from 'react'

import { errorMessage } from '../../shared/lib/errors'
import type { AccessPolicyView } from '../realm'
import type { LoreApi } from './api'
import { emptyEntity, emptyRelation, upsert } from './catalogueModel'
import type {
  CanonStatus,
  EntityType,
  LoreEntityInput,
  LoreEntityView,
  LoreRelationInput,
  LoreRelationView,
} from './model'

export type CatalogueSection = 'entities' | 'relations'

interface UseCatalogueWorkspaceOptions {
  readonly loreApi: LoreApi
  readonly policies: AccessPolicyView[]
  readonly realmId: string
}

export function useCatalogueWorkspace({ loreApi, policies, realmId }: UseCatalogueWorkspaceOptions) {
  const [entities, setEntities] = useState<LoreEntityView[]>([])
  const [relations, setRelations] = useState<LoreRelationView[]>([])
  const [section, setSection] = useState<CatalogueSection>('entities')
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
    const controller = new AbortController()
    Promise.all([
      loreApi.listLoreEntities(realmId, controller.signal),
      loreApi.listLoreRelations(realmId, controller.signal),
    ])
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
      controller.abort()
    }
  }, [loreApi, realmId])

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
        ? await loreApi.updateLoreEntity(realmId, editingEntityId, effectiveEntityDraft)
        : await loreApi.createLoreEntity(realmId, effectiveEntityDraft)
      setEntities((current) => upsert(current, saved))
      cancelEntityEdit()
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
        ? await loreApi.updateLoreRelation(realmId, editingRelationId, {
            relationType: relationDraft.relationType,
            description: relationDraft.description,
            accessPolicyId: effectiveRelationDraft.accessPolicyId,
            evidenceChunkIds: relationDraft.evidenceChunkIds,
          })
        : await loreApi.createLoreRelation(realmId, effectiveRelationDraft)
      setRelations((current) => upsert(current, saved))
      cancelRelationEdit()
    } catch (reason) {
      setError(errorMessage(reason))
    } finally {
      setSaving(false)
    }
  }

  async function promoteEntity(entity: LoreEntityView) {
    await runMutation(async () => {
      const promoted = await loreApi.promoteLoreEntity(realmId, entity.id)
      setEntities((current) => upsert(current, promoted))
    })
  }

  async function promoteRelation(relation: LoreRelationView) {
    await runMutation(async () => {
      const promoted = await loreApi.promoteLoreRelation(realmId, relation.id)
      setRelations((current) => upsert(current, promoted))
    })
  }

  async function deleteEntity(entity: LoreEntityView) {
    if (!window.confirm(`¿Retirar «${entity.displayName}» del catálogo?`)) return
    await runMutation(async () => {
      await loreApi.deleteLoreEntity(realmId, entity.id)
      setEntities((current) => current.filter((item) => item.id !== entity.id))
    })
  }

  async function deleteRelation(relation: LoreRelationView) {
    if (!window.confirm(`¿Retirar la relación «${relation.relationType}»?`)) return
    await runMutation(async () => {
      await loreApi.deleteLoreRelation(realmId, relation.id)
      setRelations((current) => current.filter((item) => item.id !== relation.id))
    })
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

  function cancelEntityEdit() {
    setEditingEntityId(null)
    setEntityDraft(emptyEntity(policies[0]?.id))
  }

  function cancelRelationEdit() {
    setEditingRelationId(null)
    setRelationDraft(emptyRelation(policies[0]?.id, entities[0]?.id, entities[1]?.id))
  }

  async function runMutation(mutation: () => Promise<void>) {
    setSaving(true)
    setError(null)
    try {
      await mutation()
    } catch (reason) {
      setError(errorMessage(reason))
    } finally {
      setSaving(false)
    }
  }

  return {
    cancelEntityEdit,
    cancelRelationEdit,
    canonFilter,
    deleteEntity,
    deleteRelation,
    editingEntityId,
    editingRelationId,
    effectiveEntityDraft,
    effectiveRelationDraft,
    entities,
    error,
    loading,
    promoteEntity,
    promoteRelation,
    relations,
    saveEntity,
    saveRelation,
    saving,
    search,
    section,
    setCanonFilter,
    setEntityDraft,
    setRelationDraft,
    setSearch,
    setSection,
    setTypeFilter,
    typeFilter,
    visibleEntities,
    visibleRelations,
    editEntity,
    editRelation,
  }
}
