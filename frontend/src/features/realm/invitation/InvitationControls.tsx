import { useRef, useState, type SubmitEvent } from 'react'

import type { InvitationView } from '../model'
import { roleLabels } from '../realmLabels'
import type { RealmAdministrationState } from '../useRealmAdministration'

export function InvitationForm({ administration }: Readonly<{
  administration: RealmAdministrationState
}>) {
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<'PLAYER' | 'EDITOR'>('PLAYER')
  const revision = useRef(0)
  const submitting = useRef(false)

  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!email.trim() || submitting.current) return
    const submittedRevision = revision.current
    submitting.current = true
    try {
      const saved = await administration.invite({ email: email.trim(), role })
      // An earlier request must never erase the next invitation being drafted.
      if (saved && revision.current === submittedRevision) {
        setEmail('')
        setRole('PLAYER')
      }
    } finally {
      submitting.current = false
    }
  }

  return (
    <form className="invite-form" onSubmit={(event) => void submit(event)}>
      <label>
        <span>Correo del jugador</span>
        <input name="memberEmail" type="email" maxLength={320} required placeholder="jugador@ejemplo.local"
          value={email} onChange={(event) => { revision.current += 1; setEmail(event.target.value) }} />
      </label>
      <label>
        <span>Rol</span>
        <select name="memberRole" value={role} onChange={(event) => {
          revision.current += 1
          setRole(event.target.value === 'EDITOR' ? 'EDITOR' : 'PLAYER')
        }}>
          <option value="PLAYER">Jugador</option>
          <option value="EDITOR">Editor</option>
        </select>
      </label>
      <button disabled={administration.inviting} type="submit">
        {administration.inviting ? 'Invitando…' : 'Invitar'}
      </button>
      <p className="muted">Comparte la dirección de la aplicación. La invitación se acepta cuando el jugador accede con este correo verificado.</p>
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
