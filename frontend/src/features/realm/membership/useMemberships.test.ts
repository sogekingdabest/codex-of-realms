import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { createRealmApi } from '../../../test/realmApi'
import type { MembershipView } from '../model'
import { useMemberships } from './useMemberships'

const owner: MembershipView = {
  userId: 'owner-1', displayName: 'Maestra', email: 'gm@example.test', role: 'OWNER',
}
const player: MembershipView = {
  userId: 'player-1', displayName: 'Nara', email: null, role: 'PLAYER',
}

describe('useMemberships', () => {
  it('carga, recarga y elimina miembros sin permitir retirar al owner', async () => {
    const api = createRealmApi({
      listMemberships: vi.fn().mockResolvedValueOnce([owner, player]).mockResolvedValueOnce([owner]),
    })
    const onError = vi.fn()
    const { result } = renderHook(() => useMemberships({
      api, enabled: true, realmId: 'realm-1', onError,
    }))

    await waitFor(() => expect(result.current.members).toEqual([owner, player]))
    await act(async () => { expect(await result.current.reload()).toBe(true) })
    expect(result.current.members).toEqual([owner])

    await act(async () => { expect(await result.current.remove(owner)).toBe(false) })
    expect(api.removeMembership).not.toHaveBeenCalled()

    await act(async () => { expect(await result.current.remove(player)).toBe(true) })
    expect(api.removeMembership).toHaveBeenCalledWith('realm-1', 'player-1')
    expect(onError).not.toHaveBeenCalled()
  })

  it('no carga para jugadores y aborta la petición al desmontar', () => {
    let signal: AbortSignal | undefined
    const onError = vi.fn()
    const api = createRealmApi({
      listMemberships: vi.fn((_realmId, nextSignal) => {
        signal = nextSignal
        return new Promise<MembershipView[]>(() => undefined)
      }),
    })
    const disabled = renderHook(() => useMemberships({
      api, enabled: false, realmId: 'realm-1', onError,
    }))
    expect(api.listMemberships).not.toHaveBeenCalled()
    disabled.unmount()

    const enabled = renderHook(() => useMemberships({
      api, enabled: true, realmId: 'realm-1', onError,
    }))
    expect(signal?.aborted).toBe(false)
    enabled.unmount()
    expect(signal?.aborted).toBe(true)
  })

  it('informa fallos al recargar y retirar miembros sin alterar el estado', async () => {
    const api = createRealmApi({
      listMemberships: vi.fn().mockResolvedValueOnce([player]).mockRejectedValueOnce(new Error('reload failed')),
      removeMembership: vi.fn().mockRejectedValue(new Error('remove failed')),
    })
    const onError = vi.fn()
    const { result } = renderHook(() => useMemberships({
      api, enabled: true, realmId: 'realm-1', onError,
    }))
    await waitFor(() => expect(result.current.members).toEqual([player]))

    await act(async () => { expect(await result.current.reload()).toBe(false) })
    await act(async () => { expect(await result.current.remove(player)).toBe(false) })
    expect(result.current.members).toEqual([player])
    expect(onError).toHaveBeenCalledTimes(2)
  })
})
