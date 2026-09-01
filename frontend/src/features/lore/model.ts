import type { SourceEvidence } from '../content'

export type EntityType = 'CHARACTER' | 'PLACE' | 'FACTION' | 'OBJECT' | 'EVENT'
export type CanonStatus = 'PROPOSED' | 'CANON'

export interface CataloguePromotionView {
  id: string
  promotedBy: string
  promotedAt: string
}

export interface LoreEntityView {
  id: string
  realmId: string
  type: EntityType
  displayName: string
  aliases: string[]
  description: string
  canonStatus: CanonStatus
  accessPolicyId: string
  sourceEvidence: SourceEvidence[]
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
  promotedBy: string | null
  promotedAt: string | null
  promotionHistory: CataloguePromotionView[]
}

export interface LoreRelationView {
  id: string
  realmId: string
  sourceEntityId: string
  sourceEntityName: string
  targetEntityId: string
  targetEntityName: string
  relationType: string
  description: string
  canonStatus: CanonStatus
  accessPolicyId: string
  sourceEvidence: SourceEvidence[]
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
  promotedBy: string | null
  promotedAt: string | null
  promotionHistory: CataloguePromotionView[]
}

export interface LoreEntityInput {
  type: EntityType
  displayName: string
  aliases: string[]
  description: string
  accessPolicyId: string
  evidenceChunkIds: string[]
}

export interface LoreRelationInput {
  sourceEntityId: string
  targetEntityId: string
  relationType: string
  description: string
  accessPolicyId: string
  evidenceChunkIds: string[]
}

export type LoreRelationUpdateInput = Omit<LoreRelationInput, 'sourceEntityId' | 'targetEntityId'>
