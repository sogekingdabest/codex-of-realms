import type {
  AccessClassification,
  AccessPolicyView,
  CurrentUserView,
  InvitationView,
  LoreAnswer,
  MembershipView,
  RealmSummary,
  RuntimeCapabilities,
  SourceContentView,
  SourceDocumentView,
} from './types'

export interface CodexApi {
  getCurrentUser(): Promise<CurrentUserView>
  createRealm(name: string): Promise<RealmSummary>
  listPolicies(realmId: string): Promise<AccessPolicyView[]>
  createPolicy(
    realmId: string,
    classification: AccessClassification,
    name?: string,
    description?: string,
  ): Promise<AccessPolicyView>
  listMemberships(realmId: string): Promise<MembershipView[]>
  listInvitations(realmId: string): Promise<InvitationView[]>
  inviteMember(
    realmId: string,
    email: string,
    role: 'EDITOR' | 'PLAYER',
  ): Promise<InvitationView>
  revokeInvitation(realmId: string, invitationId: string): Promise<void>
  removeMembership(realmId: string, userId: string): Promise<void>
  listPolicyGrants(realmId: string, policyId: string): Promise<MembershipView[]>
  grantPolicy(realmId: string, policyId: string, userId: string): Promise<void>
  revokePolicy(realmId: string, policyId: string, userId: string): Promise<void>
  listSources(realmId: string): Promise<SourceDocumentView[]>
  uploadSource(
    realmId: string,
    title: string,
    accessPolicyId: string,
    file: File,
  ): Promise<SourceDocumentView>
  deleteSource(realmId: string, documentId: string): Promise<void>
  getSourceContent(
    realmId: string,
    documentId: string,
    versionId: string,
  ): Promise<SourceContentView>
  ask(realmId: string, question: string): Promise<LoreAnswer>
  getCapabilities(): Promise<RuntimeCapabilities>
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

  createPolicy(
    realmId: string,
    classification: AccessClassification,
    name?: string,
    description?: string,
  ) {
    return this.request<AccessPolicyView>(
      `/realms/${encodeURIComponent(realmId)}/access-policies`,
      {
        method: 'POST',
        body: JSON.stringify({ classification, name, description }),
      },
    )
  }

  listMemberships(realmId: string) {
    return this.request<MembershipView[]>(
      `/realms/${encodeURIComponent(realmId)}/memberships`,
    )
  }

  listInvitations(realmId: string) {
    return this.request<InvitationView[]>(
      `/realms/${encodeURIComponent(realmId)}/invitations`,
    )
  }

  inviteMember(realmId: string, email: string, role: 'EDITOR' | 'PLAYER') {
    return this.request<InvitationView>(
      `/realms/${encodeURIComponent(realmId)}/invitations`,
      { method: 'POST', body: JSON.stringify({ email, role }) },
    )
  }

  revokeInvitation(realmId: string, invitationId: string) {
    return this.request<void>(
      `/realms/${encodeURIComponent(realmId)}/invitations/${encodeURIComponent(invitationId)}`,
      { method: 'DELETE' },
    )
  }

  removeMembership(realmId: string, userId: string) {
    return this.request<void>(
      `/realms/${encodeURIComponent(realmId)}/memberships/${encodeURIComponent(userId)}`,
      { method: 'DELETE' },
    )
  }

  listPolicyGrants(realmId: string, policyId: string) {
    return this.request<MembershipView[]>(
      `/realms/${encodeURIComponent(realmId)}/access-policies/${encodeURIComponent(policyId)}/grants`,
    )
  }

  grantPolicy(realmId: string, policyId: string, userId: string) {
    return this.request<void>(
      `/realms/${encodeURIComponent(realmId)}/access-policies/${encodeURIComponent(policyId)}/grants/${encodeURIComponent(userId)}`,
      { method: 'PUT' },
    )
  }

  revokePolicy(realmId: string, policyId: string, userId: string) {
    return this.request<void>(
      `/realms/${encodeURIComponent(realmId)}/access-policies/${encodeURIComponent(policyId)}/grants/${encodeURIComponent(userId)}`,
      { method: 'DELETE' },
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

  deleteSource(realmId: string, documentId: string) {
    return this.request<void>(
      `/realms/${encodeURIComponent(realmId)}/sources/${encodeURIComponent(documentId)}`,
      { method: 'DELETE' },
    )
  }

  getSourceContent(realmId: string, documentId: string, versionId: string) {
    return this.request<SourceContentView>(
      `/realms/${encodeURIComponent(realmId)}/sources/${encodeURIComponent(documentId)}/versions/${encodeURIComponent(versionId)}/content`,
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

  getCapabilities() {
    return this.request<RuntimeCapabilities>('/capabilities')
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
    if (response.status === 204) return undefined as T
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
