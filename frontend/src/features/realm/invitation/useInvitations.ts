import { useCallback, useEffect, useState } from 'react'

import { isAbortError } from '../../../shared/lib/errors'
import type { RealmApi } from '../api'
import type { InvitationView } from '../model'

export interface InvitationInput {
  readonly email: string
  readonly role: 'EDITOR' | 'PLAYER'
}

interface UseInvitationsOptions {
  readonly api: RealmApi
  readonly enabled: boolean
  readonly realmId: string
  readonly onError: (error: unknown) => void
  readonly onMembershipsChanged: () => Promise<boolean>
  readonly onOperationStart?: () => void
}

const noop = () => undefined

export function useInvitations({ api, enabled, realmId, onError, onMembershipsChanged, onOperationStart = noop }: UseInvitationsOptions) {
  const [invitations, setInvitations] = useState<InvitationView[]>([])
  const [loading, setLoading] = useState(enabled)
  const [inviting, setInviting] = useState(false)
  const [updating, setUpdating] = useState(false)

  useEffect(() => {
    if (!enabled) return
    const controller = new AbortController()
    api.listInvitations(realmId, controller.signal)
      .then((nextInvitations) => {
        if (!controller.signal.aborted) setInvitations(nextInvitations)
      })
      .catch((reason: unknown) => {
        if (!isAbortError(reason)) onError(reason)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [api, enabled, onError, realmId])

  const invite = useCallback(async ({ email, role }: InvitationInput) => {
    if (!enabled) return false
    onOperationStart()
    setInviting(true)
    try {
      const invitation = await api.inviteMember(realmId, email, role)
      setInvitations((current) => [invitation, ...current.filter((item) => item.id !== invitation.id)])
      return await onMembershipsChanged()
    } catch (reason) {
      onError(reason)
      return false
    } finally {
      setInviting(false)
    }
  }, [api, enabled, onError, onMembershipsChanged, onOperationStart, realmId])

  const revoke = useCallback(async (invitation: InvitationView) => {
    if (!enabled) return false
    onOperationStart()
    setUpdating(true)
    try {
      await api.revokeInvitation(realmId, invitation.id)
      setInvitations((current) => current.map((item) => (
        item.id === invitation.id ? { ...item, status: 'REVOKED' } : item
      )))
      return true
    } catch (reason) {
      onError(reason)
      return false
    } finally {
      setUpdating(false)
    }
  }, [api, enabled, onError, onOperationStart, realmId])

  return { invitations, inviting, loading, revoke, invite, updating }
}
