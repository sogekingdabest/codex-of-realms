import type { AuthenticatedHttpClient } from '../../shared/api'
import type { SourceChunkView, SourceContentView, SourceDocumentView, SourceJobView, SourceSubmission } from './model'

export interface ContentApi {
  listSources(realmId: string, signal?: AbortSignal): Promise<SourceDocumentView[]>
  uploadSource(realmId: string, title: string, accessPolicyId: string, file: File, key?: string): Promise<SourceSubmission>
  replaceSource(realmId: string, documentId: string, accessPolicyId: string, file: File, key?: string): Promise<SourceSubmission>
  reprocessSource(realmId: string, documentId: string, key?: string): Promise<SourceSubmission>
  listSourceJobs(realmId: string, signal?: AbortSignal): Promise<SourceJobView[]>
  getSourceJob(realmId: string, jobId: string, signal?: AbortSignal): Promise<SourceJobView>
  retrySourceJob(realmId: string, jobId: string): Promise<SourceJobView>
  deleteSource(realmId: string, documentId: string): Promise<void>
  getSourceContent(realmId: string, documentId: string, versionId: string, signal?: AbortSignal): Promise<SourceContentView>
  listSourceChunks(realmId: string, documentId: string, signal?: AbortSignal): Promise<SourceChunkView[]>
}

export class HttpContentApi implements ContentApi {
  private readonly submissionKeys = new WeakMap<File, Map<string, string>>()
  private fileKey(file: File, scope: string) {
    let keys = this.submissionKeys.get(file)
    if (!keys) { keys = new Map(); this.submissionKeys.set(file, keys) }
    let key = keys.get(scope)
    if (!key) { key = crypto.randomUUID(); keys.set(scope, key) }
    return key
  }
  constructor(private readonly http: AuthenticatedHttpClient) {}

  listSources(realmId: string, signal?: AbortSignal) {
    return this.http.request<SourceDocumentView[]>(`/realms/${id(realmId)}/sources`, { signal })
  }

  uploadSource(realmId: string, title: string, accessPolicyId: string, file: File, key?: string) {
    key ??= this.fileKey(file, JSON.stringify(['upload', realmId, title, accessPolicyId]))
    const body = new FormData()
    body.append('title', title)
    body.append('accessPolicyId', accessPolicyId)
    body.append('file', file)
    return this.http.request<SourceSubmission>(`/realms/${id(realmId)}/sources`, { method: 'POST', body, headers: { 'Idempotency-Key': key } })
  }


  replaceSource(realmId: string, documentId: string, accessPolicyId: string, file: File, key?: string) {
    key ??= this.fileKey(file, JSON.stringify(['replace', realmId, documentId, accessPolicyId]))
    const body = new FormData()
    body.append('accessPolicyId', accessPolicyId)
    body.append('file', file)
    return this.http.request<SourceSubmission>(`/realms/${id(realmId)}/sources/${id(documentId)}`, { method: 'PUT', body, headers: { 'Idempotency-Key': key } })
  }
  reprocessSource(realmId: string, documentId: string, key: string = crypto.randomUUID()) {
    return this.http.request<SourceSubmission>(`/realms/${id(realmId)}/sources/${id(documentId)}/reprocess`, { method: 'POST', headers: { 'Idempotency-Key': key } })
  }
  listSourceJobs(realmId: string, signal?: AbortSignal) {
    return this.http.request<SourceJobView[]>(`/realms/${id(realmId)}/source-jobs`, { signal })
  }
  getSourceJob(realmId: string, jobId: string, signal?: AbortSignal) {
    return this.http.request<SourceJobView>(`/realms/${id(realmId)}/source-jobs/${id(jobId)}`, { signal })
  }
  retrySourceJob(realmId: string, jobId: string) {
    return this.http.request<SourceJobView>(`/realms/${id(realmId)}/source-jobs/${id(jobId)}/retry`, { method: 'POST' })
  }
  deleteSource(realmId: string, documentId: string) {
    return this.http.request<void>(`/realms/${id(realmId)}/sources/${id(documentId)}`, { method: 'DELETE' })
  }

  getSourceContent(realmId: string, documentId: string, versionId: string, signal?: AbortSignal) {
    return this.http.request<SourceContentView>(`/realms/${id(realmId)}/sources/${id(documentId)}/versions/${id(versionId)}/content`, { signal })
  }

  listSourceChunks(realmId: string, documentId: string, signal?: AbortSignal) {
    return this.http.request<SourceChunkView[]>(`/realms/${id(realmId)}/sources/${id(documentId)}/chunks`, { signal })
  }
}

const id = encodeURIComponent
