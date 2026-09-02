import { vi } from 'vitest'

import type { RealmApi } from '../features/realm'

export function createRealmApi(overrides: Partial<RealmApi> = {}): RealmApi {
  return {
    getCurrentUser: vi.fn(),
    createRealm: vi.fn(),
    listPolicies: vi.fn().mockResolvedValue([]),
    createPolicy: vi.fn(),
    listMemberships: vi.fn().mockResolvedValue([]),
    listInvitations: vi.fn().mockResolvedValue([]),
    inviteMember: vi.fn(),
    revokeInvitation: vi.fn(),
    removeMembership: vi.fn(),
    listPolicyGrants: vi.fn().mockResolvedValue([]),
    grantPolicy: vi.fn(),
    revokePolicy: vi.fn(),
    ...overrides,
  }
}
