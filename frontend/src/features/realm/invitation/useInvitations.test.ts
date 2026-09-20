import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { createRealmInvitationApi } from '../../../test/realmApi'
import type { InvitationView } from '../model'
import { useInvitations } from './useInvitations'

const pending: InvitationView = {
  id: 'invite-1', realmId: 'realm-1', email: 'player@example.test', role: 'PLAYER',
  status: 'PENDING', acceptedUserId: null, createdAt: '2026-09-01T10:00:00Z',
}

describe('useInvitations', () => {
  it('deduplica invitaciones, refresca membresías y permite revocarlas', async () => {
    const updated = { ...pending, email: 'updated@example.test' }
    const api = createRealmInvitationApi({
      listInvitations: vi.fn().mockResolvedValue([pending]),
      inviteMember: vi.fn().mockResolvedValue(updated),
    })
    const onMembershipsChanged = vi.fn().mockResolvedValue(true)
    const onError = vi.fn()
    const { result } = renderHook(() => useInvitations({
      api,
      enabled: true,
      realmId: 'realm-1',
      onError,
      onMembershipsChanged,
    }))

    await waitFor(() => expect(result.current.invitations).toEqual([pending]))
    await act(async () => {
      expect(await result.current.invite({ email: updated.email, role: 'PLAYER' })).toBe(true)
    })
    expect(result.current.invitations).toEqual([updated])
    expect(onMembershipsChanged).toHaveBeenCalledOnce()

    await act(async () => { expect(await result.current.revoke(updated)).toBe(true) })
    expect(api.revokeInvitation).toHaveBeenCalledWith('realm-1', 'invite-1')
    expect(result.current.invitations[0]?.status).toBe('REVOKED')
  })

  it('no consulta ni muta invitaciones cuando no es owner', async () => {
    const api = createRealmInvitationApi()
    const onError = vi.fn()
    const onMembershipsChanged = vi.fn().mockResolvedValue(true)
    const { result } = renderHook(() => useInvitations({
      api,
      enabled: false,
      realmId: 'realm-1',
      onError,
      onMembershipsChanged,
    }))

    expect(api.listInvitations).not.toHaveBeenCalled()
    await act(async () => {
      expect(await result.current.invite({ email: 'player@example.test', role: 'PLAYER' })).toBe(false)
      expect(await result.current.revoke(pending)).toBe(false)
    })
    expect(api.inviteMember).not.toHaveBeenCalled()
    expect(api.revokeInvitation).not.toHaveBeenCalled()
  })

  it('informa fallos de invitación y revocación', async () => {
    const api = createRealmInvitationApi({
      inviteMember: vi.fn().mockRejectedValue(new Error('invite failed')),
      revokeInvitation: vi.fn().mockRejectedValue(new Error('revoke failed')),
    })
    const onError = vi.fn()
    const onMembershipsChanged = vi.fn().mockResolvedValue(true)
    const { result } = renderHook(() => useInvitations({
      api, enabled: true, realmId: 'realm-1', onError, onMembershipsChanged,
    }))
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => {
      expect(await result.current.invite({ email: pending.email, role: 'PLAYER' })).toBe(false)
      expect(await result.current.revoke(pending)).toBe(false)
    })
    expect(onError).toHaveBeenCalledTimes(2)
    expect(onMembershipsChanged).not.toHaveBeenCalled()
  })
})
