import type { SubmitEvent } from 'react'

import { formText } from '../../../shared/lib/forms'
import type { InvitationView } from '../model'
import { roleLabels } from '../realmLabels'
import type { RealmAdministrationState } from '../useRealmAdministration'

export function InvitationForm({ administration }: Readonly<{
  administration: RealmAdministrationState
}>) {
  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const email = formText(form, 'memberEmail')
    if (!email) return
    const saved = await administration.invite({
      email,
      role: form.get('memberRole') === 'EDITOR' ? 'EDITOR' : 'PLAYER',
    })
    if (saved) formElement.reset()
  }

  return (
    <form className="invite-form" onSubmit={(event) => void submit(event)}>
      <label>
        <span>Correo de Keycloak</span>
        <input name="memberEmail" type="email" maxLength={320} required placeholder="jugador@ejemplo.local" />
      </label>
      <label>
        <span>Rol</span>
        <select name="memberRole" defaultValue="PLAYER">
          <option value="PLAYER">Jugador</option>
          <option value="EDITOR">Editor</option>
        </select>
      </label>
      <button disabled={administration.inviting} type="submit">
        {administration.inviting ? 'Invitando…' : 'Invitar'}
      </button>
    </form>
  )
}

export function PendingInvitations({ invitations, updating, onRevoke }: Readonly<{
  invitations: InvitationView[]
  updating: boolean
  onRevoke: (invitation: InvitationView) => Promise<boolean>
}>) {
  return (
    <div className="pending-list">
      <h4>Invitaciones pendientes</h4>
      {invitations.map((invitation) => (
        <article key={invitation.id}>
          <span>{invitation.email} · {roleLabels[invitation.role]}</span>
          <button
            className="text-danger"
            disabled={updating}
            type="button"
            onClick={() => void onRevoke(invitation)}
          >
            Revocar
          </button>
        </article>
      ))}
    </div>
  )
}
