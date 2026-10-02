import type { SourceDocumentView, SourceJobView } from './model'

export const unfinished = (job: SourceJobView) => job.state !== 'SUCCEEDED' && job.state !== 'CANCELLED'

/**
 * New documents on their way to the library: the latest unfinished job of each document that has no
 * published version yet. The library only lists published versions.
 */
export function pendingSources(jobs: SourceJobView[], sources: SourceDocumentView[]) {
  const published = new Set(sources.map((source) => source.id))
  const latest = new Map<string, SourceJobView>()
  for (const job of jobs) {
    const current = latest.get(job.documentId)
    if (!current || job.createdAt > current.createdAt) latest.set(job.documentId, job)
  }
  return [...latest.values()].filter((job) => unfinished(job) && !published.has(job.documentId))
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
