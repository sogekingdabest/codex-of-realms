import { useEffect, useState } from 'react'
import type { ContentApi, SourceEvidence } from '../../content'

/** Read the exact version referenced by the ficha; never substitute the current version. */
export function EvidencePreview({ contentApi, realmId, evidence }: Readonly<{
  contentApi: Pick<ContentApi, 'getSourceContent'>
  realmId: string
  evidence: SourceEvidence
}>) {
  const [excerpt, setExcerpt] = useState<string | null>(null)
  const [unavailable, setUnavailable] = useState(false)
  const { documentId, documentVersionId, startOffset, endOffset } = evidence
  useEffect(() => {
    const controller = new AbortController()
    contentApi.getSourceContent(realmId, documentId, documentVersionId, controller.signal)
      .then((source) => {
        if (controller.signal.aborted) return
        if (startOffset < 0 || endOffset <= startOffset || endOffset > source.content.length) {
          setUnavailable(true)
          return
        }
        setExcerpt(source.content.slice(startOffset, endOffset))
      })
      .catch(() => { if (!controller.signal.aborted) setUnavailable(true) })
    return () => controller.abort()
  }, [contentApi, realmId, documentId, documentVersionId, startOffset, endOffset])
  if (unavailable) return <p className="muted">No se pudo mostrar el fragmento. Abre la fuente para volver a intentarlo.</p>
  if (excerpt === null) return <p className="muted" role="status">Cargando fragmento…</p>
  return <blockquote className="evidence-preview">{excerpt}</blockquote>
}
