import type { MembershipView } from '../model'
import { roleLabels } from '../realmLabels'

interface MembershipListProps {
  readonly isOwner: boolean
  readonly members: MembershipView[]
  readonly updating: boolean
  readonly onRemove: (member: MembershipView) => Promise<boolean>
}

export function MembershipList({ isOwner, members, updating, onRemove }: MembershipListProps) {
  async function remove(member: MembershipView) {
    if (member.role === 'OWNER' || !window.confirm(`¿Quitar a ${member.displayName} de este universo?`)) return
    await onRemove(member)
  }

  return (
    <div className="member-list">
      {members.map((member) => (
        <article key={member.userId}>
          <div>
            <strong>{member.displayName}</strong>
            <span>{member.email || 'Sin correo'} · {roleLabels[member.role]}</span>
          </div>
          {isOwner && member.role !== 'OWNER' && (
            <button
              className="text-danger"
              disabled={updating}
              type="button"
              onClick={() => void remove(member)}
            >
              Quitar
            </button>
          )}
        </article>
      ))}
    </div>
  )
}
