import { useCallback, useMemo } from 'react'

import type { RealmApi } from './api'
import { useAccessPolicies } from './access/useAccessPolicies'
import { useInvitations } from './invitation/useInvitations'
import { useMemberships } from './membership/useMemberships'
import type { MembershipView } from './model'

interface UseRealmAdministrationOptions {
  readonly api: RealmApi
  readonly canEdit: boolean
  readonly isOwner: boolean
  readonly realmId: string
  readonly onError: (error: unknown) => void
  readonly onOperationStart?: () => void
}

const noop = () => undefined

export function useRealmAdministration({
  api,
  canEdit,
  isOwner,
  realmId,
  onError,
  onOperationStart = noop,
}: UseRealmAdministrationOptions) {
  const memberships = useMemberships({ api, enabled: canEdit, realmId, onError, onOperationStart })
  const invitations = useInvitations({
    api,
    enabled: isOwner,
    realmId,
    onError,
    onMembershipsChanged: memberships.reload,
    onOperationStart,
  })
  const access = useAccessPolicies({ api, enabled: canEdit, realmId, onError, onOperationStart })

  const removeMember = useCallback(async (member: MembershipView) => {
    if (!isOwner) return false
    const removed = await memberships.remove(member)
    if (removed) access.forgetGrantee(member.userId)
    return removed
  }, [access, isOwner, memberships])

  const playerMembers = useMemo(
    () => memberships.members.filter((member) => member.role === 'PLAYER'),
    [memberships.members],
  )
  const spoilerPolicies = useMemo(
    () => access.policies.filter((policy) => policy.classification === 'SPOILER'),
    [access.policies],
  )

  return {
    createPolicy: access.create,
    creatingPolicy: access.creating,
    grantsByPolicy: access.grantsByPolicy,
    invitationLoading: invitations.loading,
    invitationUpdating: invitations.updating,
    invitations: invitations.invitations,
    invite: invitations.invite,
    inviting: invitations.inviting,
    isOwner,
    membershipLoading: memberships.loading,
    membershipUpdating: memberships.updating,
    members: memberships.members,
    playerMembers,
    policies: access.policies,
    policyLoading: access.loading,
    policyUpdating: access.updating,
    removeMember,
    revokeInvitation: invitations.revoke,
    selectedPolicyId: access.selectedPolicyId,
    setSelectedPolicyId: access.setSelectedPolicyId,
    spoilerPolicies,
    toggleGrant: access.toggleGrant,
  }
}

export type RealmAdministrationState = ReturnType<typeof useRealmAdministration>
