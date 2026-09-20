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
  answerMode: 'EXTRACTIVE'
  excerpts: { text: string; citationRank: number }[]
  outcome: 'ANSWERED' | 'INSUFFICIENT_EVIDENCE'
  answer: string | null
  citations: Citation[]
  provenance: AnswerProvenance
  failureReason: 'NO_EVIDENCE' | 'LOW_RELEVANCE' | 'UNSAFE_INPUT' | 'MODEL_UNAVAILABLE' | 'VALIDATION_FAILED' | null
}
