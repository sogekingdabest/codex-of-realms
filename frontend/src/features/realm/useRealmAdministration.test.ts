import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { createRealmAdministrationApi } from '../../test/realmApi'
import type { AccessPolicyView, InvitationView, MembershipView } from './model'
import { useRealmAdministration } from './useRealmAdministration'

const player: MembershipView = {
  userId: 'player-1', displayName: 'Nara', email: null, role: 'PLAYER',
}
const invitation: InvitationView = {
  id: 'invite-1', realmId: 'realm-1', email: 'pending@example.test', role: 'PLAYER',
  status: 'PENDING', acceptedUserId: null, createdAt: '2026-09-01T10:00:00Z',
}
const spoiler: AccessPolicyView = {
  id: 'spoiler', realmId: 'realm-1', classification: 'SPOILER', name: 'La Aguja', description: null,
}

describe('useRealmAdministration', () => {
  it('conserva miembros e invitaciones cuando falla la carga independiente de políticas', async () => {
    const error = new Error('Políticas temporalmente no disponibles')
    const api = createRealmAdministrationApi({
      listMemberships: vi.fn().mockResolvedValue([player]),
      listInvitations: vi.fn().mockResolvedValue([invitation]),
      listPolicies: vi.fn().mockRejectedValue(error),
    })
    const onError = vi.fn()
    const { result } = renderHook(() => useRealmAdministration({
      api, canEdit: true, isOwner: true, realmId: 'realm-1', onError,
    }))

    await waitFor(() => expect(result.current.members).toEqual([player]))
    await waitFor(() => expect(result.current.invitations).toEqual([invitation]))
    await waitFor(() => expect(onError).toHaveBeenCalledWith(error))
    expect(result.current.policies).toEqual([])
  })

  it('coordina la retirada del miembro con la limpieza de grants', async () => {
    const api = createRealmAdministrationApi({
      listMemberships: vi.fn().mockResolvedValue([player]),
      listPolicies: vi.fn().mockResolvedValue([spoiler]),
      listPolicyGrants: vi.fn().mockResolvedValue([player]),
    })
    const onError = vi.fn()
    const { result } = renderHook(() => useRealmAdministration({
      api, canEdit: true, isOwner: true, realmId: 'realm-1', onError,
    }))
    await waitFor(() => expect(result.current.grantsByPolicy.spoiler).toEqual(['player-1']))

    await act(async () => { expect(await result.current.removeMember(player)).toBe(true) })
    expect(result.current.members).toEqual([])
    expect(result.current.grantsByPolicy.spoiler).toEqual([])
  })

  it('no activa ningún slice administrativo para jugadores', () => {
    const api = createRealmAdministrationApi()
    const onError = vi.fn()
    renderHook(() => useRealmAdministration({
      api, canEdit: false, isOwner: false, realmId: 'realm-1', onError,
    }))
    expect(api.listPolicies).not.toHaveBeenCalled()
    expect(api.listMemberships).not.toHaveBeenCalled()
    expect(api.listInvitations).not.toHaveBeenCalled()
  })

  it('no limpia grants cuando la membresía protegida no puede retirarse', async () => {
    const api = createRealmAdministrationApi()
    const onError = vi.fn()
    const owner: MembershipView = {
      userId: 'owner-1', displayName: 'Maestra', email: null, role: 'OWNER',
    }
    const { result } = renderHook(() => useRealmAdministration({
      api, canEdit: true, isOwner: true, realmId: 'realm-1', onError,
    }))

    await act(async () => { expect(await result.current.removeMember(owner)).toBe(false) })
    expect(api.removeMembership).not.toHaveBeenCalled()
  })

  it('impide que un editor retire membresías aunque necesite cargarlas para los grants', async () => {
    const api = createRealmAdministrationApi({
      listMemberships: vi.fn().mockResolvedValue([player]),
    })
    const onError = vi.fn()
    const { result } = renderHook(() => useRealmAdministration({
      api, canEdit: true, isOwner: false, realmId: 'realm-1', onError,
    }))
    await waitFor(() => expect(result.current.members).toEqual([player]))

    await act(async () => { expect(await result.current.removeMember(player)).toBe(false) })
    expect(api.removeMembership).not.toHaveBeenCalled()
    expect(api.listInvitations).not.toHaveBeenCalled()
  })
})
