import { useEffect, useMemo, useState } from 'react'

import { errorMessage } from '../../../shared/lib/errors'
import type { ContentApi, SourceDocumentView } from '../../content'

interface EvidencePickerProps {
  readonly contentApi: ContentApi
  readonly evidenceChunkIds: string[]
  readonly policyId: string
  readonly realmId: string
  readonly sources: SourceDocumentView[]
  readonly onChange: (ids: string[]) => void
}

export function EvidencePicker({ contentApi, evidenceChunkIds, policyId, realmId, sources, onChange }: EvidencePickerProps) {
  const eligibleSources = useMemo(
    () => sources.filter((source) => source.status === 'READY' && source.accessPolicyId === policyId),
    [policyId, sources],
  )
  const [documentId, setDocumentId] = useState(() => eligibleSources[0]?.id ?? '')
  const [chunks, setChunks] = useState<Awaited<ReturnType<ContentApi['listSourceChunks']>>>([])
  const [loadedDocumentId, setLoadedDocumentId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const selectedDocumentId = eligibleSources.some((source) => source.id === documentId)
    ? documentId
    : eligibleSources[0]?.id ?? ''
  const loading = Boolean(selectedDocumentId) && loadedDocumentId !== selectedDocumentId

  useEffect(() => {
    if (!selectedDocumentId) return
    let active = true
    const controller = new AbortController()
    contentApi.listSourceChunks(realmId, selectedDocumentId, controller.signal)
      .then((nextChunks) => {
        if (active) {
          setChunks(nextChunks)
          setLoadedDocumentId(selectedDocumentId)
          setError(null)
        }
      })
      .catch((reason: unknown) => {
        if (active) setError(errorMessage(reason))
      })
    return () => {
      active = false
      controller.abort()
    }
  }, [contentApi, realmId, selectedDocumentId])

  function toggle(chunkId: string) {
    if (evidenceChunkIds.includes(chunkId)) {
      onChange(evidenceChunkIds.filter((id) => id !== chunkId))
    } else if (evidenceChunkIds.length < 20) {
      onChange([...evidenceChunkIds, chunkId])
    }
  }

  return (
    <fieldset className="evidence-picker">
      <legend>Evidencia de fuente <span>{evidenceChunkIds.length}/20</span></legend>
      {eligibleSources.length === 0 ? (
        <p>No hay fuentes listas con esta misma visibilidad. La ficha puede guardarse sin evidencia.</p>
      ) : (
        <>
          <label>
            <span>Fuente</span>
            <select value={selectedDocumentId} onChange={(event) => setDocumentId(event.target.value)}>
              {eligibleSources.map((source) => <option key={source.id} value={source.id}>{source.title}</option>)}
            </select>
          </label>
          {error && <p className="field-error">{error}</p>}
          <ChunkOptions
            chunks={chunks}
            evidenceChunkIds={evidenceChunkIds}
            loading={loading}
            onToggle={toggle}
          />
        </>
      )}
    </fieldset>
  )
}

function ChunkOptions({ chunks, evidenceChunkIds, loading, onToggle }: Readonly<{
  chunks: Awaited<ReturnType<ContentApi['listSourceChunks']>>
  evidenceChunkIds: string[]
  loading: boolean
  onToggle: (chunkId: string) => void
}>) {
  if (loading) return <div className="chunk-options" aria-live="polite"><p>Abriendo fragmentos…</p></div>
  return (
    <div className="chunk-options" aria-live="polite">
      {chunks.map((chunk) => {
        const label = chunk.heading || `Fragmento ${chunk.ordinal + 1}`
        return (
          <label key={chunk.id}>
            <input
              aria-label={`Seleccionar ${label}`}
              checked={evidenceChunkIds.includes(chunk.id)}
              disabled={!evidenceChunkIds.includes(chunk.id) && evidenceChunkIds.length >= 20}
              type="checkbox"
              onChange={() => onToggle(chunk.id)}
            />
            <span>
              <strong>{label}</strong>
              <small>{excerpt(chunk.content)}</small>
            </span>
          </label>
        )
      })}
    </div>
  )
}

function excerpt(content: string) {
  const normalized = content.replaceAll(/\s+/g, ' ').trim()
  return normalized.length > 180 ? `${normalized.slice(0, 177)}…` : normalized
}
