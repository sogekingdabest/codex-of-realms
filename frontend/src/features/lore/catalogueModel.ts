import type {
  EntityType,
  LoreEntityInput,
  LoreRelationInput,
} from './model'

export const entityTypeLabels: Record<EntityType, string> = {
  CHARACTER: 'Personaje',
  PLACE: 'Lugar',
  FACTION: 'Facción',
  OBJECT: 'Objeto',
  EVENT: 'Evento',
}

export const emptyEntity = (policyId = ''): LoreEntityInput => ({
  type: 'CHARACTER',
  displayName: '',
  aliases: [],
  description: '',
  accessPolicyId: policyId,
  evidenceChunkIds: [],
})

export const emptyRelation = (
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

export function upsert<T extends { id: string }>(items: T[], value: T) {
  return [value, ...items.filter((item) => item.id !== value.id)]
}
