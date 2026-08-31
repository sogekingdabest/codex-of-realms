export type RealmRole = 'OWNER' | 'EDITOR' | 'PLAYER'
export type AccessClassification = 'PUBLIC' | 'GM_ONLY' | 'SPOILER'
export type ProcessingStatus = 'PROCESSING' | 'READY' | 'FAILED'
export type EntityType = 'CHARACTER' | 'PLACE' | 'FACTION' | 'OBJECT' | 'EVENT'
export type CanonStatus = 'PROPOSED' | 'CANON'

export interface AuthenticatedUser {
  id: string
  issuer: string
  subject: string
  displayName: string
  email: string | null
}

export interface RealmSummary {
  id: string
  name: string
  role: RealmRole
}

export interface CurrentUserView {
  user: AuthenticatedUser
  realms: RealmSummary[]
}

export interface AccessPolicyView {
  id: string
  realmId: string
  classification: AccessClassification
  name: string
  description: string | null
}

export interface MembershipView {
  userId: string
  displayName: string
  email: string | null
  role: RealmRole
}

export interface InvitationView {
  id: string
  realmId: string
  email: string
  role: 'EDITOR' | 'PLAYER'
  status: 'PENDING' | 'ACCEPTED' | 'REVOKED'
  acceptedUserId: string | null
  createdAt: string
}

export interface SourceDocumentView {
  id: string
  realmId: string
  title: string
  versionId: string
  versionNumber: number
  checksumSha256: string
  originalFilename: string
  mediaType: string
  language: string
  status: ProcessingStatus
  accessPolicyId: string
  embeddingProvider: string | null
  embeddingModel: string | null
  embeddingDimension: number | null
  chunkCount: number
  createdAt: string
}

export interface Citation {
  rank: number
  realmId: string
  chunkId: string
  sourceDocumentId: string
  documentVersionId: string
  versionNumber: number
  sourceTitle: string
  heading: string | null
  startOffset: number
  endOffset: number
}

export interface SourceContentView {
  documentId: string
  versionId: string
  title: string
  originalFilename: string
  content: string
}

export interface SourceChunkView {
  id: string
  documentId: string
  documentVersionId: string
  sourceTitle: string
  ordinal: number
  heading: string | null
  content: string
  startOffset: number
  endOffset: number
}

export interface SourceEvidence {
  documentId: string
  documentVersionId: string
  chunkId: string
  sourceTitle: string
  checksumSha256: string
  heading: string | null
  startOffset: number
  endOffset: number
}

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

export type LoreRelationUpdateInput = Omit<
  LoreRelationInput,
  'sourceEntityId' | 'targetEntityId'
>

export type ModelCapabilityStatus =
  | 'READY'
  | 'MODEL_MISSING'
  | 'RUNTIME_UNAVAILABLE'
  | 'NOT_CONFIGURED'

export interface ModelCapability {
  provider: string
  model: string
  available: boolean
  status: ModelCapabilityStatus
  installedModels: string[]
}

export interface RuntimeCapabilities {
  chat: ModelCapability
  embedding: ModelCapability
}

export interface AnswerProvenance {
  embeddingProvider: string
  embeddingModel: string
  chatProvider: string
  chatModel: string
}

export interface LoreAnswer {
  outcome: 'ANSWERED' | 'INSUFFICIENT_EVIDENCE'
  answer: string | null
  citations: Citation[]
  provenance: AnswerProvenance
  failureReason:
    | 'NO_EVIDENCE'
    | 'LOW_RELEVANCE'
    | 'UNSAFE_INPUT'
    | 'MODEL_UNAVAILABLE'
    | 'VALIDATION_FAILED'
    | null
}
