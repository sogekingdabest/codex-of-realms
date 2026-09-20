import { vi } from 'vitest'

import type {
  RealmAccessPolicyApi,
  RealmAdministrationApi,
  RealmApi,
  RealmInvitationApi,
  RealmMembershipApi,
} from '../features/realm'

export function createRealmAccessPolicyApi(
  overrides: Partial<RealmAccessPolicyApi> = {},
): RealmAccessPolicyApi {
  return {
    listPolicies: vi.fn().mockResolvedValue([]),
    createPolicy: vi.fn(),
    listPolicyGrants: vi.fn().mockResolvedValue([]),
    grantPolicy: vi.fn(),
    revokePolicy: vi.fn(),
    ...overrides,
  }
}

export function createRealmMembershipApi(
  overrides: Partial<RealmMembershipApi> = {},
): RealmMembershipApi {
  return {
    listMemberships: vi.fn().mockResolvedValue([]),
    removeMembership: vi.fn(),
    ...overrides,
  }
}

export function createRealmInvitationApi(
  overrides: Partial<RealmInvitationApi> = {},
): RealmInvitationApi {
  return {
    listInvitations: vi.fn().mockResolvedValue([]),
    inviteMember: vi.fn(),
    revokeInvitation: vi.fn(),
    ...overrides,
  }
}

export function createRealmAdministrationApi(
  overrides: Partial<RealmAdministrationApi> = {},
): RealmAdministrationApi {
  return {
    ...createRealmAccessPolicyApi(),
    ...createRealmMembershipApi(),
    ...createRealmInvitationApi(),
    ...overrides,
  }
}

export function createRealmApi(overrides: Partial<RealmApi> = {}): RealmApi {
  return {
    getCurrentUser: vi.fn(),
    createRealm: vi.fn(),
    ...createRealmAdministrationApi(),
    ...overrides,
  }
}
