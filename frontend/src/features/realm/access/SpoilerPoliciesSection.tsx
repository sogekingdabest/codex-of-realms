import type { SubmitEvent } from 'react'

import { formText } from '../../../shared/lib/forms'
import type { MembershipView } from '../model'
import type { RealmAdministrationState } from '../useRealmAdministration'

export function SpoilerPoliciesSection({ administration }: Readonly<{
  administration: RealmAdministrationState
}>) {
  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const name = formText(form, 'policyName')
    if (!name) return
    const created = await administration.createPolicy({
      name,
      description: formText(form, 'policyDescription') || undefined,
    })
    if (created) formElement.reset()
  }

  return (
    <div className="access-section">
      <h3>Grupos de spoiler</h3>
      <form className="policy-form" onSubmit={(event) => void submit(event)}>
        <label>
          <span>Nombre</span>
          <input name="policyName" maxLength={120} required placeholder="Secreto de la Aguja" />
        </label>
        <label>
          <span>Descripción opcional</span>
          <input name="policyDescription" maxLength={300} placeholder="Revelado tras el capítulo 4" />
        </label>
        <button disabled={administration.creatingPolicy} type="submit">
          {administration.creatingPolicy ? 'Creando…' : 'Crear grupo'}
        </button>
      </form>
      {administration.spoilerPolicies.length === 0 ? (
        <p className="muted">Crea un grupo cuando una fuente deba revelarse solo a ciertos jugadores.</p>
      ) : (
        <div className="spoiler-list">
          {administration.spoilerPolicies.map((policy) => (
            <article key={policy.id}>
              <div>
                <strong>{policy.name}</strong>
                {policy.description && <span>{policy.description}</span>}
              </div>
              <PolicyGrantList
                grants={administration.grantsByPolicy[policy.id] ?? []}
                members={administration.playerMembers}
                policyId={policy.id}
                updating={administration.policyUpdating}
                onToggle={administration.toggleGrant}
              />
            </article>
          ))}
        </div>
      )}
    </div>
  )
}

function PolicyGrantList({ grants, members, policyId, updating, onToggle }: Readonly<{
  grants: string[]
  members: MembershipView[]
  policyId: string
  updating: boolean
  onToggle: (policyId: string, member: MembershipView) => Promise<boolean>
}>) {
  if (members.length === 0) return <small>Invita jugadores para conceder este conocimiento.</small>
  return (
    <div className="grant-list">
      {members.map((member) => (
        <label key={member.userId}>
          <input
            type="checkbox"
            disabled={updating}
            checked={grants.includes(member.userId)}
            onChange={() => void onToggle(policyId, member)}
          />
          <span>{member.displayName}</span>
        </label>
      ))}
    </div>
  )
}
