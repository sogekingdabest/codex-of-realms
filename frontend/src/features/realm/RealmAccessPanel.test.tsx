import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import type { RealmAdministrationState } from './useRealmAdministration'
import { RealmAccessPanel } from './RealmAccessPanel'

function administration(overrides: Partial<RealmAdministrationState> = {}): RealmAdministrationState {
  return {
    createPolicy: vi.fn().mockResolvedValue(true),
    creatingPolicy: false,
    grantsByPolicy: { spoiler: ['player-1'] },
    invitationLoading: false,
    invitationUpdating: false,
    invitations: [{
      id: 'invite-1', realmId: 'realm-1', email: 'pending@example.test', role: 'PLAYER',
      status: 'PENDING', acceptedUserId: null, createdAt: '2026-09-01T10:00:00Z',
    }],
    invite: vi.fn().mockResolvedValue(true),
    inviting: false,
    isOwner: true,
    membershipLoading: false,
    membershipUpdating: false,
    members: [
      { userId: 'owner-1', displayName: 'Maestra', email: 'gm@example.test', role: 'OWNER' },
      { userId: 'player-1', displayName: 'Nara', email: null, role: 'PLAYER' },
    ],
    playerMembers: [
      { userId: 'player-1', displayName: 'Nara', email: null, role: 'PLAYER' },
    ],
    policies: [{
      id: 'spoiler', realmId: 'realm-1', classification: 'SPOILER', name: 'La Aguja', description: null,
    }],
    policyLoading: false,
    policyUpdating: false,
    removeMember: vi.fn().mockResolvedValue(true),
    revokeInvitation: vi.fn().mockResolvedValue(true),
    selectedPolicyId: 'spoiler',
    setSelectedPolicyId: vi.fn(),
    spoilerPolicies: [{
      id: 'spoiler', realmId: 'realm-1', classification: 'SPOILER', name: 'La Aguja', description: null,
    }],
    toggleGrant: vi.fn().mockResolvedValue(true),
    ...overrides,
  }
}

describe('RealmAccessPanel', () => {
  it('conserva el siguiente borrador si termina una invitación pendiente y evita el doble envío', async () => {
    let finish!: (saved: boolean) => void
    const invite = vi.fn().mockImplementationOnce(() => new Promise<boolean>((resolve) => { finish = resolve })).mockResolvedValue(true)
    render(<RealmAccessPanel administration={administration({ invite })} />)
    const email = screen.getByLabelText('Correo del jugador')
    fireEvent.change(email, { target: { value: 'first@example.test' } })
    fireEvent.click(screen.getByRole('button', { name: 'Invitar' }))
    fireEvent.change(email, { target: { value: 'second@example.test' } })
    fireEvent.change(screen.getByLabelText('Rol'), { target: { value: 'EDITOR' } })
    fireEvent.click(screen.getByRole('button', { name: 'Invitar' }))
    expect(invite).toHaveBeenCalledTimes(1)
    await act(async () => { finish(true) })
    expect(email).toHaveValue('second@example.test')
    expect(screen.getByLabelText('Rol')).toHaveValue('EDITOR')
    fireEvent.click(screen.getByRole('button', { name: 'Invitar' }))
    await waitFor(() => expect(invite).toHaveBeenLastCalledWith({ email: 'second@example.test', role: 'EDITOR' }))
    await waitFor(() => expect(email).toHaveValue(''))
  })

  it('adapta formularios y acciones del owner a comandos tipados', async () => {
    const state = administration()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<RealmAccessPanel administration={state} />)

    fireEvent.change(screen.getByLabelText('Correo del jugador'), {
      target: { value: 'editor@example.test' },
    })
    fireEvent.change(screen.getByLabelText('Rol'), { target: { value: 'EDITOR' } })
    fireEvent.click(screen.getByRole('button', { name: 'Invitar' }))
    await waitFor(() => expect(state.invite).toHaveBeenCalledWith({
      email: 'editor@example.test', role: 'EDITOR',
    }))
    expect(screen.getByLabelText('Correo del jugador')).toHaveValue('')

    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Nuevo secreto' } })
    fireEvent.change(screen.getByLabelText('Descripción opcional'), { target: { value: 'Tras el prólogo' } })
    fireEvent.click(screen.getByRole('button', { name: 'Crear grupo' }))
    await waitFor(() => expect(state.createPolicy).toHaveBeenCalledWith({
      name: 'Nuevo secreto', description: 'Tras el prólogo',
    }))

    fireEvent.click(screen.getByLabelText('Nara'))
    expect(state.toggleGrant).toHaveBeenCalledWith('spoiler', state.playerMembers[0])
    fireEvent.click(screen.getByRole('button', { name: 'Revocar' }))
    expect(state.revokeInvitation).toHaveBeenCalledWith(state.invitations[0])
    fireEvent.click(screen.getByRole('button', { name: 'Quitar' }))
    expect(confirm).toHaveBeenCalled()
    expect(state.removeMember).toHaveBeenCalledWith(state.members[1])
    confirm.mockRestore()
  })

  it('oculta invitaciones y retirada de miembros a editores pero conserva políticas', () => {
    render(<RealmAccessPanel administration={administration({ isOwner: false })} />)

    expect(screen.queryByLabelText('Correo del jugador')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Quitar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Revocar' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Crear grupo' })).toBeInTheDocument()
  })

  it('presenta los estados vacíos y bloquea cada operación de forma independiente', () => {
    render(<RealmAccessPanel administration={administration({
      creatingPolicy: true,
      grantsByPolicy: {},
      invitationUpdating: true,
      invitations: [],
      inviting: true,
      membershipUpdating: true,
      members: [],
      playerMembers: [],
      policies: [],
      policyUpdating: true,
      spoilerPolicies: [],
    })} />)

    expect(screen.getByRole('button', { name: 'Invitando…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Creando…' })).toBeDisabled()
    expect(screen.getByText('Crea un grupo cuando una fuente deba revelarse solo a ciertos jugadores.')).toBeInTheDocument()
  })

  it('explica que los grants requieren jugadores', () => {
    render(<RealmAccessPanel administration={administration({
      members: [],
      playerMembers: [],
    })} />)

    expect(screen.getByText('Invita jugadores para conceder este conocimiento.')).toBeInTheDocument()
  })
})
