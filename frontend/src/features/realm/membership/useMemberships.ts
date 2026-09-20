import { useCallback, useEffect, useState } from 'react'

import { isAbortError } from '../../../shared/lib/errors'
import type { RealmMembershipApi } from '../api'
import type { MembershipView } from '../model'

interface UseMembershipsOptions {
  readonly api: RealmMembershipApi
  readonly enabled: boolean
  readonly realmId: string
  readonly onError: (error: unknown) => void
  readonly onOperationStart?: () => void
}

const noop = () => undefined

export function useMemberships({ api, enabled, realmId, onError, onOperationStart = noop }: UseMembershipsOptions) {
  const [members, setMembers] = useState<MembershipView[]>([])
  const [loading, setLoading] = useState(enabled)
  const [updating, setUpdating] = useState(false)

  const load = useCallback(async () => {
    if (!enabled) return false
    setLoading(true)
    try {
      setMembers(await api.listMemberships(realmId))
      return true
    } catch (reason) {
      if (!isAbortError(reason)) onError(reason)
      return false
    } finally {
      setLoading(false)
    }
  }, [api, enabled, onError, realmId])

  useEffect(() => {
    if (!enabled) return
    const controller = new AbortController()
    api.listMemberships(realmId, controller.signal)
      .then((nextMembers) => {
        if (!controller.signal.aborted) setMembers(nextMembers)
      })
      .catch((reason: unknown) => {
        if (!isAbortError(reason)) onError(reason)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [api, enabled, onError, realmId])

  const reload = useCallback(() => load(), [load])

  const remove = useCallback(async (member: MembershipView) => {
    if (!enabled || member.role === 'OWNER') return false
    onOperationStart()
    setUpdating(true)
    try {
      await api.removeMembership(realmId, member.userId)
      setMembers((current) => current.filter((item) => item.userId !== member.userId))
      return true
    } catch (reason) {
      onError(reason)
      return false
    } finally {
      setUpdating(false)
    }
  }, [api, enabled, onError, onOperationStart, realmId])

  return { loading, members, reload, remove, updating }
}
