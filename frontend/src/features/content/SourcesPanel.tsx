import type { RefObject, SubmitEvent } from 'react'

import type { AccessPolicyView } from '../realm'
import type { SourceDocumentView } from './model'

const classificationLabels = {
  PUBLIC: 'Pública', GM_ONLY: 'Solo dirección', SPOILER: 'Spoiler con permiso',
} as const

export function SourcesPanel({ canEdit, fileInput, loading, policies, selectedPolicyId, sources, uploading, onDelete, onPolicyChange, onUpload }: Readonly<{
  canEdit: boolean
  fileInput: RefObject<HTMLInputElement | null>
  loading: boolean
  policies: AccessPolicyView[]
  selectedPolicyId: string
  sources: SourceDocumentView[]
  uploading: boolean
  onDelete: (source: SourceDocumentView) => Promise<void>
  onPolicyChange: (policyId: string) => void
  onUpload: (event: SubmitEvent<HTMLFormElement>) => Promise<void>
}>) {
  return (
    <section className="panel sources-panel">
      <div className="panel-heading">
        <span className="panel-number">01</span>
        <div><p className="eyebrow">Biblioteca del realm</p><h2>Fuentes</h2></div>
        <span className="count">{sources.length}</span>
      </div>
      {canEdit && (
        <div className="upload-card">
          <h3>Añadir conocimiento</h3>
          {policies.length > 0 ? (
            <form className="upload-form" onSubmit={(event) => void onUpload(event)}>
              <label><span>Título</span><input name="title" maxLength={160} required placeholder="Crónica de Lumbrevela" /></label>
              <label>
                <span>Visibilidad</span>
                <select value={selectedPolicyId} onChange={(event) => onPolicyChange(event.target.value)} required>
                  {policies.map((policy) => <option key={policy.id} value={policy.id}>{policy.name} · {classificationLabels[policy.classification]}</option>)}
                </select>
              </label>
              <label className="file-field"><span>Archivo Markdown o TXT</span><input ref={fileInput} name="file" type="file" accept=".md,.txt,text/markdown,text/plain" required /></label>
              <button disabled={uploading} type="submit">{uploading ? 'Procesando…' : 'Subir y procesar'}</button>
            </form>
          ) : <p className="muted">Preparando las políticas base del universo…</p>}
        </div>
      )}
      <SourceList canEdit={canEdit} loading={loading} sources={sources} onDelete={onDelete} />
    </section>
  )
}

function SourceList({ canEdit, loading, sources, onDelete }: Readonly<{
  canEdit: boolean
  loading: boolean
  sources: SourceDocumentView[]
  onDelete: (source: SourceDocumentView) => Promise<void>
}>) {
  if (loading) return <div className="source-list" aria-live="polite"><p className="muted">Leyendo el catálogo…</p></div>
  if (sources.length === 0) return <div className="source-list" aria-live="polite"><div className="empty-state"><span aria-hidden="true">◇</span><p>Todavía no hay fuentes visibles en este universo.</p></div></div>
  return (
    <div className="source-list" aria-live="polite">
      {sources.map((source) => (
        <article className="source-item" key={source.id}>
          <div><h3>{source.title}</h3><p>{source.originalFilename} · versión {source.versionNumber}</p></div>
          <div className="source-meta">
            <span className={`status status-${source.status.toLowerCase()}`}>{sourceStatusLabel(source.status)}</span>
            <small>{source.chunkCount} fragmentos</small>
            {canEdit && <button className="text-danger" type="button" onClick={() => void onDelete(source)}>Eliminar</button>}
          </div>
        </article>
      ))}
    </div>
  )
}

function sourceStatusLabel(status: SourceDocumentView['status']) {
  if (status === 'READY') return 'Lista'
  if (status === 'FAILED') return 'Fallida'
  return 'Procesando'
}
