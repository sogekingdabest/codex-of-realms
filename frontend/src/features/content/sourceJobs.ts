import type { SourceDocumentView, SourceJobView } from './model'

export const stateLabels = { UPLOADING: 'Guardando archivo', QUEUED: 'En cola', RUNNING: 'Procesando', SUCCEEDED: 'Completado', FAILED: 'Necesita atención', CANCELLED: 'Cancelado' }

export const unfinished = (job: SourceJobView) => job.state !== 'SUCCEEDED' && job.state !== 'CANCELLED'

/** State, fragments once the file is split, and attempts once there was more than one. */
export function jobProgress(job: SourceJobView) {
  const parts = [stateLabels[job.state]]
  if (job.totalChunks > 0) parts.push(`${job.completedChunks}/${job.totalChunks} fragmentos`)
  if (job.attempts > 1) parts.push(`${job.attempts} intentos`)
  return parts.join(' · ')
}

function latestByDocument(jobs: SourceJobView[]) {
  const latest = new Map<string, SourceJobView>()
  for (const job of jobs) {
    const current = latest.get(job.documentId)
    if (!current || job.createdAt > current.createdAt) latest.set(job.documentId, job)
  }
  return latest
}

/**
 * New documents on their way to the library: the latest unfinished job of each document that has no
 * published version yet. The library only lists published versions.
 */
export function pendingSources(jobs: SourceJobView[], sources: SourceDocumentView[]) {
  const published = new Set(sources.map((source) => source.id))
  return [...latestByDocument(jobs).values()].filter((job) => unfinished(job) && !published.has(job.documentId))
}

/** The unfinished job that will replace a published version, shown beside the source it replaces. */
export function replacementJob(jobs: SourceJobView[], source: SourceDocumentView) {
  const job = latestByDocument(jobs).get(source.id)
  return job && unfinished(job) && job.versionId !== source.versionId ? job : undefined
}

export function pendingSummary(pending: SourceJobView[]) {
  const failed = pending.filter((job) => job.state === 'FAILED').length
  const processing = pending.length - failed
  const parts: string[] = []
  if (processing === 1) parts.push('Procesando 1 documento nuevo; aparecerá en la lista al terminar.')
  if (processing > 1) parts.push(`Procesando ${processing} documentos nuevos; aparecerán en la lista al terminar.`)
  if (failed === 1) parts.push('1 documento nuevo necesita atención.')
  if (failed > 1) parts.push(`${failed} documentos nuevos necesitan atención.`)
  return parts.join(' ')
}
