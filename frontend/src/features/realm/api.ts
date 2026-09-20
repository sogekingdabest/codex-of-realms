import type { AuthenticatedHttpClient } from '../../shared/api'
import type {
  AccessClassification,
  AccessPolicyView,
  CurrentUserView,
  InvitationView,
  MembershipView,
  RealmSummary,
} from './model'

export interface RealmIdentityApi {
  getCurrentUser(signal?: AbortSignal): Promise<CurrentUserView>
}

export interface RealmLifecycleApi {
  createRealm(name: string): Promise<RealmSummary>
}

export interface RealmAccessPolicyApi {
  listPolicies(realmId: string, signal?: AbortSignal): Promise<AccessPolicyView[]>
  createPolicy(realmId: string, classification: AccessClassification, name?: string, description?: string): Promise<AccessPolicyView>
  listPolicyGrants(realmId: string, policyId: string, signal?: AbortSignal): Promise<MembershipView[]>
  grantPolicy(realmId: string, policyId: string, userId: string): Promise<void>
  revokePolicy(realmId: string, policyId: string, userId: string): Promise<void>
}

export interface RealmMembershipApi {
  listMemberships(realmId: string, signal?: AbortSignal): Promise<MembershipView[]>
  removeMembership(realmId: string, userId: string): Promise<void>
}

export interface RealmInvitationApi {
  listInvitations(realmId: string, signal?: AbortSignal): Promise<InvitationView[]>
  inviteMember(realmId: string, email: string, role: 'EDITOR' | 'PLAYER'): Promise<InvitationView>
  revokeInvitation(realmId: string, invitationId: string): Promise<void>
}

export type RealmApi = RealmIdentityApi
  & RealmLifecycleApi
  & RealmAccessPolicyApi
  & RealmMembershipApi
  & RealmInvitationApi

export class HttpRealmApi implements RealmApi {
  constructor(private readonly http: AuthenticatedHttpClient) {}

  getCurrentUser(signal?: AbortSignal) { return this.http.request<CurrentUserView>('/me', { signal }) }
  createRealm(name: string) { return this.http.request<RealmSummary>('/realms', { method: 'POST', body: JSON.stringify({ name }) }) }
  listPolicies(realmId: string, signal?: AbortSignal) { return this.http.request<AccessPolicyView[]>(`/realms/${id(realmId)}/access-policies`, { signal }) }
  createPolicy(realmId: string, classification: AccessClassification, name?: string, description?: string) { return this.http.request<AccessPolicyView>(`/realms/${id(realmId)}/access-policies`, { method: 'POST', body: JSON.stringify({ classification, name, description }) }) }
  listMemberships(realmId: string, signal?: AbortSignal) { return this.http.request<MembershipView[]>(`/realms/${id(realmId)}/memberships`, { signal }) }
  listInvitations(realmId: string, signal?: AbortSignal) { return this.http.request<InvitationView[]>(`/realms/${id(realmId)}/invitations`, { signal }) }
  inviteMember(realmId: string, email: string, role: 'EDITOR' | 'PLAYER') { return this.http.request<InvitationView>(`/realms/${id(realmId)}/invitations`, { method: 'POST', body: JSON.stringify({ email, role }) }) }
  revokeInvitation(realmId: string, invitationId: string) { return this.http.request<void>(`/realms/${id(realmId)}/invitations/${id(invitationId)}`, { method: 'DELETE' }) }
  removeMembership(realmId: string, userId: string) { return this.http.request<void>(`/realms/${id(realmId)}/memberships/${id(userId)}`, { method: 'DELETE' }) }
  listPolicyGrants(realmId: string, policyId: string, signal?: AbortSignal) { return this.http.request<MembershipView[]>(`/realms/${id(realmId)}/access-policies/${id(policyId)}/grants`, { signal }) }
  grantPolicy(realmId: string, policyId: string, userId: string) { return this.http.request<void>(`/realms/${id(realmId)}/access-policies/${id(policyId)}/grants/${id(userId)}`, { method: 'PUT' }) }
  revokePolicy(realmId: string, policyId: string, userId: string) { return this.http.request<void>(`/realms/${id(realmId)}/access-policies/${id(policyId)}/grants/${id(userId)}`, { method: 'DELETE' }) }
}

const id = encodeURIComponent
