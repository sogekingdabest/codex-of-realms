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

export function promotionLabel(count: number) {
  if (count === 0) return 'Sin promociones'
  return count === 1 ? '1 promoción' : `${count} promociones`
}

/** `MANTIENE_UNA_DEUDA_CON` reads as «mantiene una deuda con». */
export function relationVerb(relationType: string) {
  return relationType.replaceAll('_', ' ').toLocaleLowerCase('es')
}

export function editorSubmitLabel(saving: boolean, editing: boolean) {
  if (saving) return 'Guardando…'
  return editing ? 'Guardar y devolver a propuesto' : 'Crear como propuesta'
}
