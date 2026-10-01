import { useState, type RefObject, type SubmitEvent } from 'react'

import type { VisibilityAudience } from '../../shared/lib/visibility'
import { Link } from '../../shared/routing'
import { DangerButton } from '../../shared/ui/DangerButton'
import { VisibilityMark } from '../../shared/ui/VisibilityMark'

import type { AccessPolicyView } from '../realm'
import type { SourceDocumentView, SourceJobView } from './model'
import { SourceJobsPanel, RecoveryActions } from './SourceJobsPanel'

const classificationLabels = {
  PUBLIC: 'Pública', GM_ONLY: 'Solo dirección', SPOILER: 'Spoiler con permiso',
} as const

export function SourcesPanel({ canEdit, audience, fileInput, loading, policies, selectedPolicyId, sources, uploading, jobs, selectedId, sourceHref, onRecover, onRetry, onDelete, onPolicyChange, onUpload }: Readonly<{
  canEdit: boolean
  audience: VisibilityAudience
  fileInput: RefObject<HTMLInputElement | null>
  loading: boolean
  policies: AccessPolicyView[]
  selectedPolicyId: string
  sources: SourceDocumentView[]
  jobs: SourceJobView[]
  selectedId?: string
  sourceHref: (source: SourceDocumentView) => string
  onRetry: (id: string) => Promise<void>
  onRecover: (documentId: string, policyId: string, file?: File) => Promise<boolean>
  uploading: boolean
  onDelete: (source: SourceDocumentView) => Promise<void>
  onPolicyChange: (policyId: string) => void
  onUpload: (event: SubmitEvent<HTMLFormElement>) => Promise<void>
}>) {
  const [search, setSearch] = useState('')
  const normalize = (value: string) => value.normalize('NFD').replace(/\p{M}/gu, '').toLocaleLowerCase('es')
  const query = normalize(search.trim())
  const visibleSources = sources.filter((source) => normalize(`${source.title} ${source.originalFilename}`).includes(query))
  return (
    <section className="panel sources-panel">
      <div className="panel-heading">
        <div><h2>Documentos</h2></div>
        <span className="count">{sources.length}</span>
      </div>
      {sources.length > 0 && <label className="source-search"><span>Buscar fuentes</span>
        <input type="search" aria-label="Buscar fuentes" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Título o nombre del archivo" />
        <small>{visibleSources.length} de {sources.length} fuentes</small>
      </label>}
      {sources.length > 0 && visibleSources.length === 0
        ? <p className="empty-state" role="status">No hay fuentes que coincidan con la búsqueda.</p>
        : <SourceList canEdit={canEdit} audience={audience} loading={loading} sources={visibleSources} onDelete={onDelete} busy={uploading} onRecover={onRecover} selectedId={selectedId} sourceHref={sourceHref} />}
      {canEdit && (
        <details className="upload-card" open={sources.length === 0}>
          <summary>Añadir conocimiento</summary>
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
              <button disabled={uploading} type="submit">{uploading ? 'Guardando…' : 'Subir y procesar'}</button>
            </form>
          ) : <p className="muted">Preparando las políticas base del universo…</p>}
        </details>
      )}
      {canEdit && <SourceJobsPanel jobs={jobs} sources={sources} busy={uploading} onRetry={onRetry} onRecover={onRecover} />}
    </section>
  )
}

function SourceList({ canEdit, audience, loading, sources, onDelete, busy, onRecover, selectedId, sourceHref }: Readonly<{
  canEdit: boolean
  audience: VisibilityAudience
  loading: boolean
  sources: SourceDocumentView[]
  onDelete: (source: SourceDocumentView) => Promise<void>
  busy: boolean
  onRecover: (documentId: string, policyId: string, file?: File) => Promise<boolean>
  selectedId?: string
  sourceHref: (source: SourceDocumentView) => string
}>) {
  if (loading) return <div className="source-list" aria-live="polite"><p className="muted">Leyendo el catálogo…</p></div>
  if (sources.length === 0) return <div className="source-list" aria-live="polite"><div className="empty-state"><span aria-hidden="true">◇</span><p>Todavía no hay fuentes visibles en este universo.</p></div></div>
  return (
    <div className="source-list" aria-live="polite">
      {sources.map((source) => (
        <article className={`source-item${source.id === selectedId ? ' selected' : ''}`} key={source.id}>
          <Link className="source-index-link" href={sourceHref(source)} aria-label={`Leer ${source.title}`} aria-current={source.id === selectedId ? 'page' : undefined}><span>{source.title}</span>{canEdit && <small>{source.originalFilename} · v{source.versionNumber}</small>}</Link>
          <div className="source-meta">
            <VisibilityMark visibility={source.visibility} viewer={audience.viewer} revealedTo={audience.revealedTo(source.accessPolicyId)} />
            {source.status !== 'READY' && <span className={`status status-${source.status.toLowerCase()}`}>{sourceStatusLabel(source.status)}</span>}
          </div>
          {canEdit && <details className="source-actions"><summary>Gestionar</summary>
              <RecoveryActions documentId={source.id} policyId={source.accessPolicyId} title={source.title} busy={busy} onRecover={onRecover} />
              <DangerButton onClick={() => void onDelete(source)}>Eliminar</DangerButton>
          </details>}
        </article>
      ))}
    </div>
  )
}

function sourceStatusLabel(status: SourceDocumentView['status']) {
  return status === 'FAILED' ? 'Fallida' : 'Procesando'
}
