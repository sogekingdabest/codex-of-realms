import type { SourceDocumentView, SourceJobView } from './model'

const stateLabels = { UPLOADING: 'Guardando archivo', QUEUED: 'En cola', RUNNING: 'Procesando', SUCCEEDED: 'Completado', FAILED: 'Necesita atención', CANCELLED: 'Cancelado' }
const errorLabels: Record<string, string> = {
  FILE_UNAVAILABLE: 'Falta una copia íntegra del archivo. Cárgalo nuevamente.',
  MODEL_UNAVAILABLE: 'El modelo no respondió. Puedes reintentar el procesamiento.',
  WORKER_INTERRUPTED: 'El procesamiento se interrumpió. Se recuperará desde el archivo original.',
  PIPELINE_CHANGED: 'La configuración ha cambiado. Reprocesa para crear una nueva versión.',
  PROCESSING_INTERRUPTED: 'Esta operación anterior quedó incompleta. Reprocesa o carga el archivo de nuevo.',
  PROCESSING_REJECTED: 'No se pudo completar el procesamiento. Revisa tus permisos y la configuración.',
  INVALID_SOURCE: 'El archivo no contiene fragmentos utilizables.',
  INVALID_EMBEDDINGS: 'El modelo devolvió un resultado incompatible.',
  SOURCE_UNAVAILABLE: 'La operación ya no está disponible.',
}

export function SourceJobsPanel({ jobs, sources, busy, onRetry, onRecover }: Readonly<{
  jobs: SourceJobView[]
  sources: SourceDocumentView[]
  busy: boolean
  onRetry: (id: string) => Promise<void>
  onRecover: (documentId: string, policyId: string, file?: File) => Promise<boolean>
}>) {
  if (jobs.length === 0) return null
  const finished = (job: SourceJobView) => job.state === 'SUCCEEDED' || job.state === 'CANCELLED'
  function renderJob(job: SourceJobView) {
      const published = sources.find((source) => source.id === job.documentId)
      return <article className="source-job" key={job.id}>
        <h4>{job.title} · versión {job.versionNumber}</h4>
        <p>{stateLabels[job.state]} · {job.completedChunks}/{job.totalChunks} fragmentos · {job.attempts} intentos</p>
        {job.state === 'RUNNING' && <progress aria-label={`Progreso de ${job.title}`} max={job.totalChunks || 1} value={job.completedChunks} />}
        {published && published.versionId !== job.versionId && <p className="muted">La versión {published.versionNumber} sigue publicada y disponible para consultas.</p>}
        {job.errorCode && <p>{errorLabels[job.errorCode] ?? 'La operación necesita revisión.'}</p>}
        {job.state === 'QUEUED' && job.errorCode && <p className="muted">Próximo intento: {new Date(job.nextAttemptAt).toLocaleTimeString()}</p>}
        {job.state === 'FAILED' && <div className="source-recovery">
          <button type="button" disabled={busy} onClick={() => void onRetry(job.id)}>Reintentar</button>
          <RecoveryActions documentId={job.documentId} policyId={job.accessPolicyId} title={job.title} busy={busy} onRecover={onRecover} />
        </div>}
        {job.history.length > 0 && <details><summary>Historial de la operación</summary><ul>{job.history.map((event, index) =>
          <li key={index}>{stateLabels[event.state]} · intento {event.attempt} · {new Date(event.createdAt).toLocaleString()}</li>,
        )}</ul></details>}
      </article>
  }
  return <section className="source-jobs" aria-label="Procesamiento de fuentes">
    <h3>Procesamiento de fuentes</h3>
    {jobs.filter((job) => !finished(job)).map(renderJob)}
    {jobs.some(finished) && <details>
      <summary>Operaciones finalizadas ({jobs.filter(finished).length})</summary>
      {jobs.filter(finished).map(renderJob)}
    </details>}
  </section>
}

export function RecoveryActions({ documentId, policyId, title, busy, onRecover }: Readonly<{
  documentId: string; policyId: string; title: string; busy: boolean
  onRecover: (documentId: string, policyId: string, file?: File) => Promise<boolean>
}>) {
  return <div className="source-recovery">
    <button type="button" disabled={busy} onClick={() => void onRecover(documentId, policyId)}>Reprocesar</button>
    <label><span>Reemplazar archivo de {title}</span><input type="file" accept=".md,.txt,text/markdown,text/plain" disabled={busy}
      onChange={(event) => { const file = event.target.files?.[0]; if (file) void onRecover(documentId, policyId, file); event.target.value = '' }} /></label>
  </div>
}
