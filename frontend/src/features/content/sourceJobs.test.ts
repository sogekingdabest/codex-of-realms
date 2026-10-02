import { describe, expect, it } from 'vitest'

import { sourceDocument, sourceJob } from '../../test/contentApi'
import { jobProgress, pendingSources, pendingSummary, replacementJob } from './sourceJobs'

describe('pendingSources', () => {
  it('solo cuenta documentos nuevos con su último trabajo sin terminar', () => {
    const replacement = { ...sourceJob, id: 'replacement', state: 'RUNNING' as const }
    const retried = { ...sourceJob, id: 'retried', documentId: 'new', state: 'QUEUED' as const, createdAt: '2026-09-06T11:00:00Z' }
    const failedBefore = { ...retried, id: 'failed-before', state: 'FAILED' as const, createdAt: '2026-09-06T10:30:00Z' }
    const done = { ...sourceJob, id: 'done', documentId: 'done', state: 'SUCCEEDED' as const }

    expect(pendingSources([replacement, failedBefore, retried, done], [sourceDocument])).toEqual([retried])
  })

  it('describe el progreso sin fragmentos desconocidos ni un primer intento', () => {
    expect(jobProgress({ ...sourceJob, state: 'QUEUED', totalChunks: 0, attempts: 0 })).toBe('En cola')
    expect(jobProgress({ ...sourceJob, state: 'RUNNING', completedChunks: 3, totalChunks: 5, attempts: 1 })).toBe('Procesando · 3/5 fragmentos')
    expect(jobProgress({ ...sourceJob, state: 'FAILED', completedChunks: 2, totalChunks: 5, attempts: 3 })).toBe('Necesita atención · 2/5 fragmentos · 3 intentos')
  })

  it('encuentra la sustitución en curso de una fuente publicada', () => {
    const running = { ...sourceJob, state: 'RUNNING' as const }
    expect(replacementJob([running], sourceDocument)).toBe(running)
    expect(replacementJob([{ ...running, state: 'SUCCEEDED' }], sourceDocument)).toBeUndefined()
    expect(replacementJob([{ ...running, versionId: sourceDocument.versionId }], sourceDocument)).toBeUndefined()
  })

  it('resume cuántos se procesan y cuántos necesitan atención', () => {
    const running = { ...sourceJob, state: 'RUNNING' as const }
    const failed = { ...sourceJob, state: 'FAILED' as const }

    expect(pendingSummary([running])).toBe('Procesando 1 documento nuevo; aparecerá en la lista al terminar.')
    expect(pendingSummary([running, running, failed])).toBe('Procesando 2 documentos nuevos; aparecerán en la lista al terminar. 1 documento nuevo necesita atención.')
    expect(pendingSummary([failed, failed])).toBe('2 documentos nuevos necesitan atención.')
  })
})
