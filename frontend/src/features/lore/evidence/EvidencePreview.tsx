import { useEffect, useState } from 'react'
import { sourceFormat } from '../../../shared/lib/sourceMarkdown'
import { SourceText } from '../../../shared/ui/SourceText'
import type { ContentApi, SourceContentView, SourceEvidence } from '../../content'

/** Read the exact version referenced by the ficha; never substitute the current version. */
export function EvidencePreview({ contentApi, realmId, evidence }: Readonly<{
  contentApi: Pick<ContentApi, 'getSourceContent'>
  realmId: string
  evidence: SourceEvidence
}>) {
  const [source, setSource] = useState<SourceContentView | null>(null)
  const [unavailable, setUnavailable] = useState(false)
  const { documentId, documentVersionId, startOffset, endOffset } = evidence
  useEffect(() => {
    const controller = new AbortController()
    contentApi.getSourceContent(realmId, documentId, documentVersionId, controller.signal)
      .then((loaded) => {
        if (controller.signal.aborted) return
        if (startOffset < 0 || endOffset <= startOffset || endOffset > loaded.content.length) {
          setUnavailable(true)
          return
        }
        setSource(loaded)
      })
      .catch(() => { if (!controller.signal.aborted) setUnavailable(true) })
    return () => controller.abort()
  }, [contentApi, realmId, documentId, documentVersionId, startOffset, endOffset])
  if (unavailable) return <p className="muted">No se pudo mostrar el fragmento. Abre la fuente para volver a intentarlo.</p>
  if (source === null) return <p className="muted" role="status">Cargando fragmento…</p>
  return <blockquote className="evidence-preview">
    <SourceText content={source.content} format={sourceFormat(source.originalFilename)}
      passage={{ start: startOffset, end: endOffset }} display="passage" headingLevel={3} />
  </blockquote>
}
