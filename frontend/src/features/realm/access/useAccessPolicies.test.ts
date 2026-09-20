import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { createRealmAccessPolicyApi } from '../../../test/realmApi'
import type { AccessPolicyView, MembershipView } from '../model'
import { useAccessPolicies } from './useAccessPolicies'

const publicPolicy: AccessPolicyView = {
  id: 'public', realmId: 'realm-1', classification: 'PUBLIC', name: 'Público', description: null,
}
const spoilerPolicy: AccessPolicyView = {
  id: 'spoiler', realmId: 'realm-1', classification: 'SPOILER', name: 'La Aguja', description: null,
}
const player: MembershipView = {
  userId: 'player-1', displayName: 'Nara', email: null, role: 'PLAYER',
}

describe('useAccessPolicies', () => {
  it('carga grants sólo para spoilers y mantiene una selección válida', async () => {
    const api = createRealmAccessPolicyApi({
      listPolicies: vi.fn().mockResolvedValue([publicPolicy, spoilerPolicy]),
      listPolicyGrants: vi.fn().mockResolvedValue([player]),
    })
    const onError = vi.fn()
    const { result } = renderHook(() => useAccessPolicies({
      api, enabled: true, realmId: 'realm-1', onError,
    }))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.selectedPolicyId).toBe('public')
    expect(api.listPolicyGrants).toHaveBeenCalledOnce()
    expect(api.listPolicyGrants).toHaveBeenCalledWith('realm-1', 'spoiler', expect.any(AbortSignal))
    expect(result.current.grantsByPolicy).toEqual({ spoiler: ['player-1'] })
  })

  it('crea políticas, alterna grants y olvida miembros retirados', async () => {
    const created = { ...spoilerPolicy, id: 'new-policy', name: 'Nuevo secreto' }
    const api = createRealmAccessPolicyApi({
      listPolicies: vi.fn().mockResolvedValue([spoilerPolicy]),
      listPolicyGrants: vi.fn().mockResolvedValue([player]),
      createPolicy: vi.fn().mockResolvedValue(created),
    })
    const onError = vi.fn()
    const { result } = renderHook(() => useAccessPolicies({
      api, enabled: true, realmId: 'realm-1', onError,
    }))
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => {
      expect(await result.current.create({ name: 'Nuevo secreto' })).toBe(true)
    })
    expect(result.current.selectedPolicyId).toBe('new-policy')
    expect(api.createPolicy).toHaveBeenCalledWith('realm-1', 'SPOILER', 'Nuevo secreto', undefined)

    await act(async () => { expect(await result.current.toggleGrant('spoiler', player)).toBe(true) })
    expect(api.revokePolicy).toHaveBeenCalledWith('realm-1', 'spoiler', 'player-1')
    expect(result.current.grantsByPolicy.spoiler).toEqual([])

    await act(async () => { expect(await result.current.toggleGrant('spoiler', player)).toBe(true) })
    expect(api.grantPolicy).toHaveBeenCalledWith('realm-1', 'spoiler', 'player-1')
    act(() => result.current.forgetGrantee('player-1'))
    expect(result.current.grantsByPolicy.spoiler).toEqual([])
  })

  it('no hace peticiones ni mutaciones para jugadores', async () => {
    const api = createRealmAccessPolicyApi()
    const onError = vi.fn()
    const { result } = renderHook(() => useAccessPolicies({
      api, enabled: false, realmId: 'realm-1', onError,
    }))
    expect(api.listPolicies).not.toHaveBeenCalled()
    expect(api.listPolicyGrants).not.toHaveBeenCalled()
    await act(async () => {
      expect(await result.current.create({ name: 'Secreto' })).toBe(false)
      expect(await result.current.toggleGrant('spoiler', player)).toBe(false)
    })
    expect(api.createPolicy).not.toHaveBeenCalled()
    expect(api.grantPolicy).not.toHaveBeenCalled()
  })

  it('informa fallos de creación y grants sin actualizar el estado', async () => {
    const api = createRealmAccessPolicyApi({
      createPolicy: vi.fn().mockRejectedValue(new Error('create failed')),
      grantPolicy: vi.fn().mockRejectedValue(new Error('grant failed')),
    })
    const onError = vi.fn()
    const { result } = renderHook(() => useAccessPolicies({
      api, enabled: true, realmId: 'realm-1', onError,
    }))
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => {
      expect(await result.current.create({ name: 'Secreto' })).toBe(false)
      expect(await result.current.toggleGrant('spoiler', player)).toBe(false)
    })
    expect(result.current.policies).toEqual([])
    expect(result.current.grantsByPolicy).toEqual({})
    expect(onError).toHaveBeenCalledTimes(2)
  })
})
