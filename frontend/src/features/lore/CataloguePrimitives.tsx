import type { SourceEvidence } from '../content'
import type { AccessPolicyView } from '../realm'
import type { CanonStatus } from './model'

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

export function CatalogueEvidence({ evidence, onOpen }: Readonly<{
  evidence: SourceEvidence[]
  onOpen: (evidence: SourceEvidence) => void
}>) {
  if (evidence.length === 0) return <p className="no-evidence">Afirmación manual sin fuente vinculada.</p>
  return (
    <div className="catalogue-evidence">
      {evidence.map((item) => (
        <button key={item.chunkId} type="button" onClick={() => onOpen(item)}>
          <span>{item.sourceTitle}</span>
          <small>{item.heading || 'Documento'} · {item.startOffset}–{item.endOffset}</small>
        </button>
      ))}
    </div>
  )
}

export function CanonBadge({ status }: Readonly<{ status: CanonStatus }>) {
  return <span className={`canon-badge canon-${status.toLocaleLowerCase('es')}`}>{status === 'CANON' ? 'Canon' : 'Propuesto'}</span>
}

export function CatalogueEmpty({ text }: Readonly<{ text: string }>) {
  return <div className="catalogue-empty"><span aria-hidden="true">◇</span><p>{text}</p></div>
}
