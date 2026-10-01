import type { ReactNode } from 'react'
import type { SourceEvidence } from '../content'
import type { AccessPolicyView } from '../realm'

export function PolicySelect({ policies, value, onChange }: Readonly<{
  policies: AccessPolicyView[]
  value: string
  onChange: (value: string) => void
}>) {
  return (
    <label>
      <span>Visibilidad de la afirmación</span>
      <select required value={value} onChange={(event) => onChange(event.target.value)}>
        {policies.map((policy) => <option key={policy.id} value={policy.id}>{policy.name}</option>)}
      </select>
    </label>
  )
}

export function CatalogueEvidence({ evidence, onOpen, mark }: Readonly<{
  evidence: SourceEvidence[]
  onOpen: (evidence: SourceEvidence) => void
  /** Evidence shares the visibility of the claim it supports. */
  mark?: ReactNode
}>) {
  if (evidence.length === 0) return <p className="no-evidence">Afirmación manual sin fuente vinculada.</p>
  return (
    <div className="catalogue-evidence">
      {evidence.map((item) => (
        <div className="catalogue-evidence-item" key={item.chunkId}>
          <button type="button" onClick={() => onOpen(item)}>
            <span>{item.sourceTitle}</span>
            <small>{item.heading || 'Documento'}</small>
          </button>
          {mark}
        </div>
      ))}
    </div>
  )
}

export function CatalogueEmpty({ text }: Readonly<{ text: string }>) {
  return <div className="catalogue-empty"><span aria-hidden="true">◇</span><p>{text}</p></div>
}
