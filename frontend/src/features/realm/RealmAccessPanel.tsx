import { SpoilerPoliciesSection } from './access/SpoilerPoliciesSection'
import { InvitationForm, PendingInvitations } from './invitation/InvitationControls'
import { MembershipList } from './membership/MembershipList'
import type { RealmAdministrationState } from './useRealmAdministration'

export function RealmAccessPanel({ administration }: Readonly<{
  administration: RealmAdministrationState
}>) {
  const pendingInvitations = administration.invitations.filter((invitation) => invitation.status === 'PENDING')
  return (
    <section className="panel access-panel">
      <div className="panel-heading">
        <div><p className="eyebrow">Colaboración sin spoilers</p><h2>Miembros y revelaciones</h2></div>
        <span className="count">{administration.members.length}</span>
      </div>
      <div className="access-columns">
        <div className="access-section">
          <h3>Miembros</h3>
          {administration.isOwner && <InvitationForm administration={administration} />}
          <MembershipList
            isOwner={administration.isOwner}
            members={administration.members}
            updating={administration.membershipUpdating}
            onRemove={administration.removeMember}
          />
          {administration.isOwner && pendingInvitations.length > 0 && (
            <PendingInvitations
              invitations={pendingInvitations}
              updating={administration.invitationUpdating}
              onRevoke={administration.revokeInvitation}
            />
          )}
        </div>
        <SpoilerPoliciesSection administration={administration} />
      </div>
    </section>
  )
}
