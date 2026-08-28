import type {
  AccessClassification,
  AccessPolicyView,
  CurrentUserView,
  LoreAnswer,
  RealmSummary,
  SourceDocumentView,
} from './types'

export interface CodexApi {
  getCurrentUser(): Promise<CurrentUserView>
  createRealm(name: string): Promise<RealmSummary>
  listPolicies(realmId: string): Promise<AccessPolicyView[]>
  createPolicy(
    realmId: string,
    classification: AccessClassification,
  ): Promise<AccessPolicyView>
  listSources(realmId: string): Promise<SourceDocumentView[]>
  uploadSource(
    realmId: string,
    title: string,
    accessPolicyId: string,
    file: File,
  ): Promise<SourceDocumentView>
  ask(realmId: string, question: string): Promise<LoreAnswer>
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export class HttpCodexApi implements CodexApi {
  private readonly baseUrl: string

  constructor(
    baseUrl: string,
    private readonly getAccessToken: () => Promise<string>,
  ) {
    this.baseUrl = baseUrl.replace(/\/$/, '')
  }

  getCurrentUser() {
    return this.request<CurrentUserView>('/me')
  }

  createRealm(name: string) {
    return this.request<RealmSummary>('/realms', {
      method: 'POST',
      body: JSON.stringify({ name }),
    })
  }

  listPolicies(realmId: string) {
    return this.request<AccessPolicyView[]>(
      `/realms/${encodeURIComponent(realmId)}/access-policies`,
    )
  }

  createPolicy(realmId: string, classification: AccessClassification) {
    return this.request<AccessPolicyView>(
      `/realms/${encodeURIComponent(realmId)}/access-policies`,
      {
        method: 'POST',
        body: JSON.stringify({ classification }),
      },
    )
  }

  listSources(realmId: string) {
    return this.request<SourceDocumentView[]>(
      `/realms/${encodeURIComponent(realmId)}/sources`,
    )
  }

  uploadSource(
    realmId: string,
    title: string,
    accessPolicyId: string,
    file: File,
  ) {
    const body = new FormData()
    body.append('title', title)
    body.append('accessPolicyId', accessPolicyId)
    body.append('file', file)

    return this.request<SourceDocumentView>(
      `/realms/${encodeURIComponent(realmId)}/sources`,
      { method: 'POST', body },
    )
  }

  ask(realmId: string, question: string) {
    return this.request<LoreAnswer>(
      `/realms/${encodeURIComponent(realmId)}/questions`,
      {
        method: 'POST',
        body: JSON.stringify({ question }),
      },
    )
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const token = await this.getAccessToken()
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    headers.set('Authorization', `Bearer ${token}`)
    if (init.body && !(init.body instanceof FormData)) {
      headers.set('Content-Type', 'application/json')
    }

    const response = await fetch(`${this.baseUrl}${path}`, { ...init, headers })
    if (!response.ok) {
      throw new ApiError(await readError(response), response.status)
    }
    return (await response.json()) as T
  }
}

async function readError(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as {
      detail?: string
      title?: string
      message?: string
    }
    return body.detail ?? body.message ?? body.title ?? `Error HTTP ${response.status}`
  } catch {
    return `Error HTTP ${response.status}`
  }
}
