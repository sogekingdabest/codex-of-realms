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
  excludedSentences?: number
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

export type SourceJobState = 'UPLOADING' | 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
export interface SourceJobView {
  id: string
  documentId: string
  versionId: string
  versionNumber: number
  title: string
  originalFilename: string
  accessPolicyId: string
  state: SourceJobState
  attempts: number
  completedChunks: number
  totalChunks: number
  errorCode: string | null
  noOp: boolean
  nextAttemptAt: string
  createdAt: string
  history: { state: SourceJobState; attempt: number; errorCode: string | null; createdAt: string }[]
}
export interface SourceSubmission { excludedSentences?: number; job: SourceJobView; documentId: string; versionId: string }
