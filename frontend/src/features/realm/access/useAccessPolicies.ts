import { useCallback, useEffect, useState } from 'react'

import { isAbortError } from '../../../shared/lib/errors'
import type { RealmApi } from '../api'
import type { AccessPolicyView, MembershipView } from '../model'

export interface AccessPolicyInput {
  readonly description?: string
  readonly name: string
}

interface UseAccessPoliciesOptions {
  readonly api: RealmApi
  readonly enabled: boolean
  readonly realmId: string
  readonly onError: (error: unknown) => void
  readonly onOperationStart?: () => void
}

const noop = () => undefined

export function useAccessPolicies({ api, enabled, realmId, onError, onOperationStart = noop }: UseAccessPoliciesOptions) {
  const [policies, setPolicies] = useState<AccessPolicyView[]>([])
  const [grantsByPolicy, setGrantsByPolicy] = useState<Record<string, string[]>>({})
  const [selectedPolicyId, setSelectedPolicyId] = useState('')
  const [loading, setLoading] = useState(enabled)
  const [creating, setCreating] = useState(false)
  const [updating, setUpdating] = useState(false)

  useEffect(() => {
    if (!enabled) return
    const controller = new AbortController()
    const load = async () => {
      const nextPolicies = await api.listPolicies(realmId, controller.signal)
      if (controller.signal.aborted) return
      setPolicies(nextPolicies)
      setSelectedPolicyId((current) => (
        nextPolicies.some((policy) => policy.id === current) ? current : nextPolicies[0]?.id ?? ''
      ))

      const grantEntries = await Promise.all(nextPolicies
        .filter((policy) => policy.classification === 'SPOILER')
        .map(async (policy) => [
          policy.id,
          (await api.listPolicyGrants(realmId, policy.id, controller.signal)).map((member) => member.userId),
        ] as const))
      if (!controller.signal.aborted) setGrantsByPolicy(Object.fromEntries(grantEntries))
    }

    load()
      .catch((reason: unknown) => {
        if (!isAbortError(reason)) onError(reason)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [api, enabled, onError, realmId])

  const create = useCallback(async ({ description, name }: AccessPolicyInput) => {
    if (!enabled) return false
    onOperationStart()
    setCreating(true)
    try {
      const policy = await api.createPolicy(realmId, 'SPOILER', name, description)
      setPolicies((current) => [...current.filter((item) => item.id !== policy.id), policy])
      setGrantsByPolicy((current) => ({ ...current, [policy.id]: [] }))
      setSelectedPolicyId(policy.id)
      return true
    } catch (reason) {
      onError(reason)
      return false
    } finally {
      setCreating(false)
    }
  }, [api, enabled, onError, onOperationStart, realmId])

  const toggleGrant = useCallback(async (policyId: string, member: MembershipView) => {
    if (!enabled) return false
    const granted = grantsByPolicy[policyId]?.includes(member.userId) ?? false
    onOperationStart()
    setUpdating(true)
    try {
      await (granted
        ? api.revokePolicy(realmId, policyId, member.userId)
        : api.grantPolicy(realmId, policyId, member.userId))
      setGrantsByPolicy((current) => ({
        ...current,
        [policyId]: granted
          ? (current[policyId] ?? []).filter((userId) => userId !== member.userId)
          : [...(current[policyId] ?? []), member.userId],
      }))
      return true
    } catch (reason) {
      onError(reason)
      return false
    } finally {
      setUpdating(false)
    }
  }, [api, enabled, grantsByPolicy, onError, onOperationStart, realmId])

  const forgetGrantee = useCallback((userId: string) => {
    setGrantsByPolicy((current) => Object.fromEntries(
      Object.entries(current).map(([policyId, userIds]) => [
        policyId,
        userIds.filter((candidate) => candidate !== userId),
      ]),
    ))
  }, [])

  return {
    create,
    creating,
    forgetGrantee,
    grantsByPolicy,
    loading,
    policies,
    selectedPolicyId,
    setSelectedPolicyId,
    toggleGrant,
    updating,
  }
}
