import { useEffect, useId, useRef, useState } from 'react'
import { Modal } from '../../shared/ui/Modal'
import type { SourceContentView } from './model'

export interface EvidenceReference {
  documentId: string
  versionId: string
  heading: string | null
  startOffset: number
  endOffset: number
  eyebrow: string
}

export function SourceReader({ evidence, onClose }: Readonly<{
  evidence: { source: SourceContentView; returnFocusTo?: HTMLElement | null }
  onClose: () => void
}>) {
  const { source } = evidence
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    heading.current?.focus()
    const previousFocus = evidence.returnFocusTo
    return () => { if (previousFocus?.isConnected) previousFocus.focus() }
  }, [source.documentId, source.versionId, evidence.returnFocusTo])
  return <article className="source-reader" aria-label={`Fuente: ${source.title}`}>
    <header className="source-reader-heading">
      <div><p className="eyebrow">Documento original</p><h2 ref={heading} tabIndex={-1}>{source.title}</h2><p className="muted">{source.originalFilename}</p></div>
      <button className="quiet-button" type="button" onClick={onClose}>Cerrar fuente</button>
    </header>
    {(source.excludedSentences ?? 0) > 0 && <p role="status">Esta fuente contiene {source.excludedSentences} frase(s) de más de 2.000 caracteres que no pueden usarse para responder.</p>}
    <pre className="source-reader-content" aria-label="Contenido de la fuente">{source.content}</pre>
  </article>
}

export function EvidenceDialog({ evidence, onClose }: Readonly<{
  evidence: { reference: EvidenceReference | null; source: SourceContentView; returnFocusTo?: HTMLElement | null }
  onClose: () => void
}>) {
  const titleId = useId()
  const [showFullSource, setShowFullSource] = useState(evidence.reference === null)
  const { source, reference } = evidence
  return (
    <Modal labelledBy={titleId} className="source-dialog-backdrop" onClose={onClose} restoreFocusTo={evidence.returnFocusTo}>
      <div className="source-dialog">
        <header>
          <div><p className="eyebrow">{reference?.eyebrow ?? 'Lectura de fuente'}</p>
            <h2 id={titleId}>{source.title}</h2>
            <span>{source.originalFilename} · {reference?.heading || 'Documento'}</span>
          </div>
          <button data-modal-initial-focus type="button" aria-label="Cerrar evidencia" onClick={onClose}>×</button>
        </header>
        {(source.excludedSentences ?? 0) > 0 && <p role="status">Esta fuente contiene {source.excludedSentences} frase(s) de más de 2.000 caracteres que no pueden usarse para responder.</p>}
        {reference && <div className="reader-controls">
          <button type="button" className="quiet-button" aria-pressed={showFullSource}
            onClick={() => setShowFullSource((current) => !current)}>
            {showFullSource ? 'Volver al fragmento citado' : 'Leer documento completo'}
          </button>
        </div>}
        <pre tabIndex={0} aria-label="Contenido de la fuente">{showFullSource || !reference ? source.content : citationExcerpt(source.content, reference)}</pre>
      </div>
    </Modal>
  )
}

function citationExcerpt(content: string, reference: Pick<EvidenceReference, 'startOffset' | 'endOffset'>) {
  const start = Math.max(0, Math.min(reference.startOffset, content.length))
  const end = Math.max(start, Math.min(reference.endOffset, content.length))
  const contextStart = Math.max(0, start - 320)
  const contextEnd = Math.min(content.length, end + 320)
  return `${contextStart > 0 ? '…' : ''}${content.slice(contextStart, start)}⟦ ${content.slice(start, end)} ⟧${content.slice(end, contextEnd)}${contextEnd < content.length ? '…' : ''}`
}
