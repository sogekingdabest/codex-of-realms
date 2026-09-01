export type RealmRole = 'OWNER' | 'EDITOR' | 'PLAYER'
export type AccessClassification = 'PUBLIC' | 'GM_ONLY' | 'SPOILER'

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
  name: string
  description: string | null
}

export interface MembershipView {
  userId: string
  displayName: string
  email: string | null
  role: RealmRole
}

export interface InvitationView {
  id: string
  realmId: string
  email: string
  role: 'EDITOR' | 'PLAYER'
  status: 'PENDING' | 'ACCEPTED' | 'REVOKED'
  acceptedUserId: string | null
  createdAt: string
}
