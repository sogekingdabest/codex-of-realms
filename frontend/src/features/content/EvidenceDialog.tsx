import type { SourceContentView } from './model'

export interface EvidenceReference {
  documentId: string
  versionId: string
  heading: string | null
  startOffset: number
  endOffset: number
  eyebrow: string
}

export function EvidenceDialog({ evidence, onClose }: Readonly<{
  evidence: { reference: EvidenceReference; source: SourceContentView }
  onClose: () => void
}>) {
  return (
    <dialog aria-labelledby="source-dialog-title" className="source-dialog-backdrop" open><div className="source-dialog"><header><div><p className="eyebrow">{evidence.reference.eyebrow}</p><h2 id="source-dialog-title">{evidence.source.title}</h2><span>{evidence.source.originalFilename} · {evidence.reference.heading || 'Documento'}</span></div><button type="button" aria-label="Cerrar evidencia" onClick={onClose}>×</button></header><pre>{citationExcerpt(evidence.source.content, evidence.reference)}</pre></div></dialog>
  )
}

function citationExcerpt(content: string, reference: Pick<EvidenceReference, 'startOffset' | 'endOffset'>) {
  const start = Math.max(0, Math.min(reference.startOffset, content.length))
  const end = Math.max(start, Math.min(reference.endOffset, content.length))
  const contextStart = Math.max(0, start - 320)
  const contextEnd = Math.min(content.length, end + 320)
  return `${contextStart > 0 ? '…' : ''}${content.slice(contextStart, start)}⟦ ${content.slice(start, end)} ⟧${content.slice(end, contextEnd)}${contextEnd < content.length ? '…' : ''}`
}
