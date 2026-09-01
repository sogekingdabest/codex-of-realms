import type { SubmitEvent } from 'react'

import type { AccessPolicyView, InvitationView, MembershipView, RealmRole } from './model'

const roleLabels: Record<RealmRole, string> = {
  OWNER: 'Propietario', EDITOR: 'Editor', PLAYER: 'Jugador',
}

export function EmptyRealmPanel({ creating, onSubmit }: Readonly<{
  creating: boolean
  onSubmit: (event: SubmitEvent<HTMLFormElement>) => Promise<void>
}>) {
  return (
    <section className="empty-realm panel">
      <span className="panel-number">01</span>
      <div><p className="eyebrow">Primer registro</p><h2>Crea un universo</h2><p>Será tu espacio aislado para fuentes, permisos y respuestas.</p>
        <form onSubmit={(event) => void onSubmit(event)}><label><span>Nombre del universo</span><input name="realmName" maxLength={120} required placeholder="El Meridiano" /></label><button disabled={creating} type="submit">{creating ? 'Creando…' : 'Crear universo'}</button></form>
      </div>
    </section>
  )
}

export function AccessPanel(props: Readonly<{
  creatingPolicy: boolean
  grantsByPolicy: Record<string, string[]>
  invitations: InvitationView[]
  inviting: boolean
  isOwner: boolean
  members: MembershipView[]
  playerMembers: MembershipView[]
  spoilerPolicies: AccessPolicyView[]
  updating: boolean
  onCreatePolicy: (event: SubmitEvent<HTMLFormElement>) => Promise<void>
  onInvite: (event: SubmitEvent<HTMLFormElement>) => Promise<void>
  onRemoveMember: (member: MembershipView) => Promise<void>
  onRevokeInvitation: (invitation: InvitationView) => Promise<void>
  onToggleGrant: (policyId: string, member: MembershipView) => Promise<void>
}>) {
  return (
    <section className="panel access-panel">
      <div className="panel-heading"><span className="panel-number">03</span><div><p className="eyebrow">Colaboración sin spoilers</p><h2>Miembros y revelaciones</h2></div><span className="count">{props.members.length}</span></div>
      <div className="access-columns"><MembersSection {...props} /><SpoilerPoliciesSection {...props} /></div>
    </section>
  )
}

function MembersSection({ invitations, inviting, isOwner, members, updating, onInvite, onRemoveMember, onRevokeInvitation }: Readonly<{
  invitations: InvitationView[]; inviting: boolean; isOwner: boolean; members: MembershipView[]; updating: boolean
  onInvite: (event: SubmitEvent<HTMLFormElement>) => Promise<void>; onRemoveMember: (member: MembershipView) => Promise<void>; onRevokeInvitation: (invitation: InvitationView) => Promise<void>
}>) {
  const pendingInvitations = invitations.filter((invitation) => invitation.status === 'PENDING')
  return (
    <div className="access-section"><h3>Miembros</h3>
      {isOwner && <form className="invite-form" onSubmit={(event) => void onInvite(event)}><label><span>Correo de Keycloak</span><input name="memberEmail" type="email" maxLength={320} required placeholder="jugador@ejemplo.local" /></label><label><span>Rol</span><select name="memberRole" defaultValue="PLAYER"><option value="PLAYER">Jugador</option><option value="EDITOR">Editor</option></select></label><button disabled={inviting} type="submit">{inviting ? 'Invitando…' : 'Invitar'}</button></form>}
      <div className="member-list">{members.map((member) => <article key={member.userId}><div><strong>{member.displayName}</strong><span>{member.email || 'Sin correo'} · {roleLabels[member.role]}</span></div>{isOwner && member.role !== 'OWNER' && <button className="text-danger" disabled={updating} type="button" onClick={() => void onRemoveMember(member)}>Quitar</button>}</article>)}</div>
      {isOwner && pendingInvitations.length > 0 && <div className="pending-list"><h4>Invitaciones pendientes</h4>{pendingInvitations.map((invitation) => <article key={invitation.id}><span>{invitation.email} · {roleLabels[invitation.role]}</span><button className="text-danger" disabled={updating} type="button" onClick={() => void onRevokeInvitation(invitation)}>Revocar</button></article>)}</div>}
    </div>
  )
}

function SpoilerPoliciesSection({ creatingPolicy, grantsByPolicy, playerMembers, spoilerPolicies, updating, onCreatePolicy, onToggleGrant }: Readonly<{
  creatingPolicy: boolean; grantsByPolicy: Record<string, string[]>; playerMembers: MembershipView[]; spoilerPolicies: AccessPolicyView[]; updating: boolean
  onCreatePolicy: (event: SubmitEvent<HTMLFormElement>) => Promise<void>; onToggleGrant: (policyId: string, member: MembershipView) => Promise<void>
}>) {
  return (
    <div className="access-section"><h3>Grupos de spoiler</h3>
      <form className="policy-form" onSubmit={(event) => void onCreatePolicy(event)}><label><span>Nombre</span><input name="policyName" maxLength={120} required placeholder="Secreto de la Aguja" /></label><label><span>Descripción opcional</span><input name="policyDescription" maxLength={300} placeholder="Revelado tras el capítulo 4" /></label><button disabled={creatingPolicy} type="submit">{creatingPolicy ? 'Creando…' : 'Crear grupo'}</button></form>
      {spoilerPolicies.length === 0 ? <p className="muted">Crea un grupo cuando una fuente deba revelarse solo a ciertos jugadores.</p> : <div className="spoiler-list">{spoilerPolicies.map((policy) => <article key={policy.id}><div><strong>{policy.name}</strong>{policy.description && <span>{policy.description}</span>}</div><PolicyGrantList grants={grantsByPolicy[policy.id] ?? []} members={playerMembers} policyId={policy.id} updating={updating} onToggle={onToggleGrant} /></article>)}</div>}
    </div>
  )
}

function PolicyGrantList({ grants, members, policyId, updating, onToggle }: Readonly<{ grants: string[]; members: MembershipView[]; policyId: string; updating: boolean; onToggle: (policyId: string, member: MembershipView) => Promise<void> }>) {
  if (members.length === 0) return <small>Invita jugadores para conceder este conocimiento.</small>
  return <div className="grant-list">{members.map((member) => <label key={member.userId}><input type="checkbox" disabled={updating} checked={grants.includes(member.userId)} onChange={() => void onToggle(policyId, member)} /><span>{member.displayName}</span></label>)}</div>
}
