export type ProcessingStatus = 'PROCESSING' | 'READY' | 'FAILED'

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
