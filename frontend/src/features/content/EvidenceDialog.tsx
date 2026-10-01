import { useEffect, useId, useRef, useState } from 'react'
import { locatePassage, sourceFormat } from '../../shared/lib/sourceMarkdown'
import { Modal } from '../../shared/ui/Modal'
import { SourceText } from '../../shared/ui/SourceText'
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
    <div className="source-reader-content" role="region" aria-label="Contenido de la fuente">
      <SourceText content={source.content} format={sourceFormat(source.originalFilename)} />
    </div>
  </article>
}

export function EvidenceDialog({ evidence, onClose }: Readonly<{
  evidence: { reference: EvidenceReference | null; source: SourceContentView; returnFocusTo?: HTMLElement | null }
  onClose: () => void
}>) {
  const titleId = useId()
  const { source, reference } = evidence
  const passage = reference ? locatePassage(source.content, { start: reference.startOffset, end: reference.endOffset }) : null
  const [showFullSource, setShowFullSource] = useState(passage === null)
  return (
    <Modal labelledBy={titleId} className="source-dialog-backdrop" onClose={onClose} restoreFocusTo={evidence.returnFocusTo}>
      <div className="source-dialog">
        <div className="source-dialog-top">
          <header>
            <div><p className="eyebrow">{reference?.eyebrow ?? 'Lectura de fuente'}</p>
              <h2 id={titleId}>{source.title}</h2>
              <span>{source.originalFilename} · {reference?.heading || 'Documento'}</span>
            </div>
            <button data-modal-initial-focus type="button" aria-label="Cerrar evidencia" onClick={onClose}>×</button>
          </header>
          {passage && <div className="reader-controls">
            <button type="button" className="quiet-button" aria-pressed={showFullSource}
              onClick={() => setShowFullSource((current) => !current)}>
              {showFullSource ? 'Volver al fragmento citado' : 'Leer documento completo'}
            </button>
          </div>}
        </div>
        {(source.excludedSentences ?? 0) > 0 && <p className="reader-notice" role="status">Esta fuente contiene {source.excludedSentences} frase(s) de más de 2.000 caracteres que no pueden usarse para responder.</p>}
        {reference && !passage && <p className="reader-notice" role="status">No se pudo localizar el fragmento citado en esta versión. Se muestra el documento completo.</p>}
        <div className="source-dialog-content" tabIndex={0} role="region" aria-label="Contenido de la fuente">
          <SourceText content={source.content} format={sourceFormat(source.originalFilename)} passage={passage}
            display={showFullSource ? 'document' : 'context'} />
        </div>
      </div>
    </Modal>
  )
}
