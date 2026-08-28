export type RealmRole = 'OWNER' | 'EDITOR' | 'PLAYER'
export type AccessClassification = 'PUBLIC' | 'GM_ONLY' | 'SPOILER'
export type ProcessingStatus = 'PROCESSING' | 'READY' | 'FAILED'

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
}
