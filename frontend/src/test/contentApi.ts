import { vi } from 'vitest'

import type { ContentApi } from '../features/content'
import type { SourceDocumentView, SourceJobView, SourceSubmission } from '../features/content/model'

export const sourceDocument: SourceDocumentView = {
  id: 'source-1', realmId: 'realm-1', title: 'Crónica', versionId: 'version-1', versionNumber: 1,
  checksumSha256: 'checksum', originalFilename: 'cronica.md', mediaType: 'text/markdown',
  language: 'es', status: 'READY', accessPolicyId: 'public', embeddingProvider: 'ollama',
  embeddingModel: 'bge-m3', embeddingDimension: 1024, chunkCount: 2, createdAt: '2026-09-06T10:00:00Z',
}

export const sourceJob: SourceJobView = {
  id: 'job-1', documentId: sourceDocument.id, versionId: 'version-2', versionNumber: 2,
  title: sourceDocument.title, originalFilename: sourceDocument.originalFilename, accessPolicyId: 'public',
  state: 'QUEUED', attempts: 0, completedChunks: 0, totalChunks: 2, errorCode: null, noOp: false,
  nextAttemptAt: '2026-09-06T10:00:00Z', createdAt: '2026-09-06T10:00:00Z', history: [],
}

export const sourceSubmission: SourceSubmission = {
  documentId: sourceJob.documentId, versionId: sourceJob.versionId, job: sourceJob,
}

export function createContentApi(overrides: Partial<ContentApi> = {}): ContentApi {
  return {
    listSources: vi.fn().mockResolvedValue([sourceDocument]),
    listSourceJobs: vi.fn().mockResolvedValue([]),
    uploadSource: vi.fn().mockResolvedValue(sourceSubmission),
    replaceSource: vi.fn().mockResolvedValue(sourceSubmission),
    reprocessSource: vi.fn().mockResolvedValue(sourceSubmission),
    getSourceJob: vi.fn().mockResolvedValue(sourceJob),
    retrySourceJob: vi.fn().mockResolvedValue(sourceJob),
    deleteSource: vi.fn().mockResolvedValue(undefined),
    getSourceContent: vi.fn().mockResolvedValue({
      documentId: sourceDocument.id, versionId: sourceDocument.versionId,
      title: sourceDocument.title, originalFilename: sourceDocument.originalFilename, content: 'La crónica.',
    }),
    listSourceChunks: vi.fn().mockResolvedValue([]),
    ...overrides,
  }
}
