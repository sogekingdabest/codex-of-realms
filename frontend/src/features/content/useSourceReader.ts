import { useEffect, useState } from 'react'

import { isAbortError } from '../../shared/lib/errors'
import type { ContentApi } from './api'
import type { SourceContentView, SourceDocumentView } from './model'

export type SourceReaderStatus = 'idle' | 'loading' | 'ready' | 'missing' | 'failed'

interface LoadedSource {
  readonly key: string
  readonly source: SourceContentView
  readonly returnFocusTo: HTMLElement | null
}

/** Load the published version of the source named by the current location. */
export function useSourceReader({ contentApi, realmId, sourceId, sources, sourcesLoaded, onError }: Readonly<{
  contentApi: Pick<ContentApi, 'getSourceContent'>
  realmId: string
  sourceId: string | null
  sources: SourceDocumentView[]
  sourcesLoaded: boolean
  onError: (reason: unknown) => void
}>) {
  const versionId = sources.find((source) => source.id === sourceId)?.versionId ?? null
  const key = sourceId && versionId ? `${sourceId}/${versionId}` : null
  const [loaded, setLoaded] = useState<LoadedSource | null>(null)
  const [failedKey, setFailedKey] = useState<string | null>(null)

  useEffect(() => {
    if (!sourceId || !versionId) return
    // The link that opened the reader gets focus back when it closes.
    const returnFocusTo = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const requestKey = `${sourceId}/${versionId}`
    const controller = new AbortController()
    contentApi.getSourceContent(realmId, sourceId, versionId, controller.signal)
      .then((source) => {
        if (!controller.signal.aborted) setLoaded({ key: requestKey, source, returnFocusTo })
      })
      .catch((reason: unknown) => {
        if (controller.signal.aborted || isAbortError(reason)) return
        setFailedKey(requestKey)
        onError(reason)
      })
    return () => controller.abort()
  }, [contentApi, realmId, sourceId, versionId, onError])

  const reading = loaded && loaded.key === key ? loaded : null
  let status: SourceReaderStatus = 'loading'
  if (!sourceId) status = 'idle'
  else if (reading) status = 'ready'
  else if (sourcesLoaded && !versionId) status = 'missing'
  else if (key && failedKey === key) status = 'failed'
  return { status, reading }
}
