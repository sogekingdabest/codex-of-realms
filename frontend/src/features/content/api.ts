import type { AuthenticatedHttpClient } from '../../shared/api'
import type { SourceChunkView, SourceContentView, SourceDocumentView } from './model'

export interface ContentApi {
  listSources(realmId: string, signal?: AbortSignal): Promise<SourceDocumentView[]>
  uploadSource(realmId: string, title: string, accessPolicyId: string, file: File): Promise<SourceDocumentView>
  deleteSource(realmId: string, documentId: string): Promise<void>
  getSourceContent(realmId: string, documentId: string, versionId: string, signal?: AbortSignal): Promise<SourceContentView>
  listSourceChunks(realmId: string, documentId: string, signal?: AbortSignal): Promise<SourceChunkView[]>
}

export class HttpContentApi implements ContentApi {
  constructor(private readonly http: AuthenticatedHttpClient) {}

  listSources(realmId: string, signal?: AbortSignal) {
    return this.http.request<SourceDocumentView[]>(`/realms/${id(realmId)}/sources`, { signal })
  }

  uploadSource(realmId: string, title: string, accessPolicyId: string, file: File) {
    const body = new FormData()
    body.append('title', title)
    body.append('accessPolicyId', accessPolicyId)
    body.append('file', file)
    return this.http.request<SourceDocumentView>(`/realms/${id(realmId)}/sources`, { method: 'POST', body })
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
