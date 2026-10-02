import { useEffect, useId, useRef, useState } from 'react'
import { locatePassage, sourceFormat } from '../../shared/lib/sourceMarkdown'
import { playerAudience, type VisibilityAudience, type VisibilityViewer } from '../../shared/lib/visibility'
import { Link } from '../../shared/routing'
import { EyeIcon } from '../../shared/ui/icons'
import { Modal } from '../../shared/ui/Modal'
import { SourceText } from '../../shared/ui/SourceText'
import { VisibilityMark } from '../../shared/ui/VisibilityMark'
import type { SourceContentView, SourceDocumentView } from './model'

export interface EvidenceReference {
  documentId: string
  versionId: string
  heading: string | null
  startOffset: number
  endOffset: number
  eyebrow: string
  /** Identifies the exact version for owners and editors; players do not see it. */
  checksumSha256?: string
}

export function SourceReader({ evidence, published, audience = playerAudience, manageAccessHref, onClose }: Readonly<{
  evidence: { source: SourceContentView; returnFocusTo?: HTMLElement | null }
  /** The published source being read, for its visibility and version details. */
  published?: SourceDocumentView
  audience?: VisibilityAudience
  manageAccessHref?: string
  onClose: () => void
}>) {
  const { source } = evidence
  const editor = audience.viewer === 'editor'
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    heading.current?.focus()
    const previousFocus = evidence.returnFocusTo
    return () => { if (previousFocus?.isConnected) previousFocus.focus() }
  }, [source.documentId, source.versionId, evidence.returnFocusTo])
  return <article className="source-reader" aria-label={`Fuente: ${source.title}`}>
    {published?.visibility === 'SPOILER' && <SpoilerNotice published={published} audience={audience} manageAccessHref={manageAccessHref} />}
    <header className="source-reader-heading">
      <div>
        <p className="eyebrow">Documento original</p>
        <h2 ref={heading} tabIndex={-1}>{source.title}</h2>
        {editor && <p className="muted">{source.originalFilename}{published && ` · v${published.versionNumber} · ${published.chunkCount} ${published.chunkCount === 1 ? 'fragmento' : 'fragmentos'}`}</p>}
        {published?.visibility === 'GM_ONLY' && <VisibilityMark visibility="GM_ONLY" viewer={audience.viewer} />}
      </div>
      <button className="quiet-button" type="button" onClick={onClose}>Cerrar fuente</button>
    </header>
    {(source.excludedSentences ?? 0) > 0 && <p role="status">Esta fuente contiene {source.excludedSentences} frase(s) de más de 2.000 caracteres que no pueden usarse para responder.</p>}
    <section className="source-reader-content" aria-label="Contenido de la fuente">
      <SourceText content={source.content} format={sourceFormat(source.originalFilename)} />
    </section>
  </article>
}

function SpoilerNotice({ published, audience, manageAccessHref }: Readonly<{
  published: SourceDocumentView
  audience: VisibilityAudience
  manageAccessHref?: string
}>) {
  if (audience.viewer === 'player') {
    return <div className="visibility-notice" role="note">
      <EyeIcon size={22} />
      <div>
        <strong>Revelado para ti</strong>
        <span>La dirección te ha mostrado este documento. El resto del grupo no puede verlo: guárdalo para tu personaje.</span>
      </div>
    </div>
  }
  const policyName = audience.policyName(published.accessPolicyId)
  const revealedTo = audience.revealedTo(published.accessPolicyId)
  return <div className="visibility-notice" role="note">
    <EyeIcon size={22} />
    <div>
      <strong>{policyName ? `Spoiler · ${policyName}` : 'Spoiler'}</strong>
      <span>{revealedTo.length > 0 ? `Revelado a ${revealedTo.join(', ')}.` : 'Todavía no se ha revelado a ningún jugador.'} Los propietarios y editores también lo ven; el resto de jugadores, no.</span>
    </div>
    {manageAccessHref && <Link href={manageAccessHref}>Gestionar quién lo ve</Link>}
  </div>
}

export function EvidenceDialog({ evidence, viewer = 'player', onClose }: Readonly<{
  evidence: { reference: EvidenceReference | null; source: SourceContentView; returnFocusTo?: HTMLElement | null }
  /** As in the source reader, only owners and editors see the file name and checksum. */
  viewer?: VisibilityViewer
  onClose: () => void
}>) {
  const titleId = useId()
  const { source, reference } = evidence
  const editor = viewer === 'editor'
  const checksum = editor && reference?.checksumSha256 ? ` · ${reference.checksumSha256.slice(0, 10)}` : ''
  const passage = reference ? locatePassage(source.content, { start: reference.startOffset, end: reference.endOffset }) : null
  const [showFullSource, setShowFullSource] = useState(passage === null)
  return (
    <Modal labelledBy={titleId} className="source-dialog-backdrop" onClose={onClose} restoreFocusTo={evidence.returnFocusTo}>
      <div className="source-dialog">
        <div className="source-dialog-top">
          <header>
            <div><p className="eyebrow">{reference?.eyebrow ?? 'Lectura de fuente'}{checksum}</p>
              <h2 id={titleId}>{source.title}</h2>
              <span>{editor && `${source.originalFilename} · `}{reference?.heading || 'Documento'}</span>
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
