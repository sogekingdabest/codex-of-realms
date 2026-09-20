import { act, cleanup, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { createApiClients } from '../ApiClients'
import { useRealmWorkspace, type WorkspaceApis } from './useRealmWorkspace'
import { ApiError } from '../../shared/api'
import { createContentApi, sourceDocument, sourceJob, sourceSubmission } from '../../test/contentApi'
import { createRealmAdministrationApi } from '../../test/realmApi'
import type { RealmSummary } from '../../features/realm'
import type { SourceDocumentView, SourceJobView } from '../../features/content'
import type { SourceSubmission } from '../../features/content/model'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function workspaceApi(): WorkspaceApis {
  return {
    ...createApiClients('/api/v1', async () => 'token'),
    content: createContentApi(),
    realm: createRealmAdministrationApi({
      listPolicies: vi.fn().mockResolvedValue([
        { id: 'public', realmId: 'realm-1', classification: 'PUBLIC', name: 'Público', description: null },
      ]),
    }),
  }
}

async function mount(api = workspaceApi(), role: RealmSummary['role'] = 'OWNER', processingVisible = true) {
  const hook = renderHook(({ visible }) => useRealmWorkspace({
    api, realm: { id: 'realm-1', name: 'Meridiano', role }, processingVisible: visible,
  }), { initialProps: { visible: processingVisible } })
  await act(async () => {})
  return { ...hook, api }
}

async function tick(milliseconds = 2000) {
  await act(async () => { await vi.advanceTimersByTimeAsync(milliseconds) })
}

beforeEach(() => { vi.useFakeTimers() })
afterEach(() => {
  cleanup()
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('workspace: seguimiento de fuentes', () => {
  it.each([
    ['UPLOADING', 'SUCCEEDED'], ['QUEUED', 'FAILED'], ['RUNNING', 'CANCELLED'],
  ] as const)('consulta mientras está %s y se detiene al quedar %s', async (active, terminal) => {
    const api = workspaceApi()
    vi.mocked(api.content.listSourceJobs)
      .mockResolvedValueOnce([{ ...sourceJob, state: active }])
      .mockResolvedValueOnce([{ ...sourceJob, state: terminal }])
    const { result } = await mount(api)
    expect(result.current.jobs[0]?.state).toBe(active)
    expect(api.content.listSourceJobs).toHaveBeenCalledTimes(1)
    await tick(1999)
    expect(api.content.listSourceJobs).toHaveBeenCalledTimes(1)
    const published = { ...sourceDocument, versionId: 'version-2', versionNumber: 2 }
    vi.mocked(api.content.listSources).mockResolvedValue([published])
    await tick(1)
    expect(result.current.jobs[0]?.state).toBe(terminal)
    expect(result.current.sources).toEqual([published])
    await tick(10000)
    expect(api.content.listSourceJobs).toHaveBeenCalledTimes(2)
  })

  it.each(['OWNER', 'EDITOR'] as const)('permite seguir trabajos como %s', async (role) => {
    const { api } = await mount(workspaceApi(), role)
    expect(api.content.listSourceJobs).toHaveBeenCalledWith('realm-1', expect.any(AbortSignal))
  })

  it('un jugador carga fuentes sin consultar trabajos administrativos', async () => {
    const { api, result } = await mount(workspaceApi(), 'PLAYER')
    expect(result.current.sources).toEqual([sourceDocument])
    expect(result.current.sourcesLoaded).toBe(true)
    await tick()
    expect(api.content.listSourceJobs).not.toHaveBeenCalled()
  })

  it('cancela el temporizador al salir del archivo y consulta de nuevo al volver', async () => {
    const api = workspaceApi()
    vi.mocked(api.content.listSourceJobs).mockResolvedValue([sourceJob])
    const { rerender } = await mount(api)
    const signal = vi.mocked(api.content.listSourceJobs).mock.calls[0][1]!
    rerender({ visible: false })
    expect(signal.aborted).toBe(true)
    expect(vi.getTimerCount()).toBe(0)
    await tick()
    expect(api.content.listSourceJobs).toHaveBeenCalledTimes(1)
    rerender({ visible: true })
    await act(async () => {})
    expect(api.content.listSourceJobs).toHaveBeenCalledTimes(2)
  })

  it.each(['jobs', 'sources'] as const)('ignora una respuesta tardía de %s al abandonar el archivo', async (stage) => {
    const api = workspaceApi()
    const jobs = deferred<SourceJobView[]>()
    const sources = deferred<SourceDocumentView[]>()
    if (stage === 'jobs') vi.mocked(api.content.listSourceJobs).mockReturnValueOnce(jobs.promise)
    else {
      vi.mocked(api.content.listSourceJobs).mockResolvedValue([sourceJob])
      vi.mocked(api.content.listSources).mockResolvedValueOnce([sourceDocument]).mockReturnValueOnce(sources.promise)
    }
    const { result, rerender } = await mount(api)
    const previousJobs = result.current.jobs
    rerender({ visible: false })
    await act(async () => {
      jobs.resolve([sourceJob])
      sources.resolve([{ ...sourceDocument, title: 'Respuesta obsoleta' }])
    })
    expect(result.current.sources).toEqual([sourceDocument])
    expect(result.current.jobs).toEqual(previousJobs)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('aborta solicitudes al desmontar sin continuar el seguimiento', async () => {
    const api = workspaceApi()
    const pending = deferred<SourceJobView[]>()
    vi.mocked(api.content.listSourceJobs).mockReturnValue(pending.promise)
    const { unmount } = await mount(api)
    const signal = vi.mocked(api.content.listSourceJobs).mock.calls[0][1]!
    unmount()
    expect(signal.aborted).toBe(true)
    await act(async () => { pending.resolve([sourceJob]) })
    await tick(10000)
    expect(api.content.listSourceJobs).toHaveBeenCalledTimes(1)
    expect(vi.getTimerCount()).toBe(0)
  })

  it.each(['jobs', 'sources'] as const)('reintenta un fallo transitorio de %s conservando la versión publicada', async (stage) => {
    const api = workspaceApi()
    if (stage === 'jobs') vi.mocked(api.content.listSourceJobs).mockRejectedValueOnce(new Error('Sin conexión'))
    else vi.mocked(api.content.listSources).mockResolvedValueOnce([sourceDocument]).mockRejectedValueOnce(new Error('Sin conexión'))
    const { result } = await mount(api)
    expect(result.current.error).toBe('Sin conexión')
    expect(result.current.sources).toEqual([sourceDocument])
    await tick()
    expect(api.content.listSourceJobs).toHaveBeenCalledTimes(2)
    expect(result.current.sources).toEqual([sourceDocument])
  })

  it.each([401, 403, 404])('detiene el seguimiento y vacía los datos al recibir HTTP %i', async (status) => {
    const api = workspaceApi()
    vi.mocked(api.content.listSourceJobs).mockResolvedValueOnce([sourceJob])
      .mockRejectedValueOnce(new ApiError('Acceso no disponible', status))
    const { result } = await mount(api)
    await tick()
    expect(result.current.error).toBe('Acceso no disponible')
    expect(result.current.jobs).toEqual([])
    expect(result.current.sources).toEqual([])
    await tick(10000)
    expect(api.content.listSourceJobs).toHaveBeenCalledTimes(2)
  })

  it('no muestra un aviso ni reintenta una cancelación', async () => {
    const api = workspaceApi()
    vi.mocked(api.content.listSourceJobs).mockRejectedValue(new DOMException('Cancelado', 'AbortError'))
    const { result } = await mount(api)
    expect(result.current.error).toBeNull()
    await tick()
    expect(api.content.listSourceJobs).toHaveBeenCalledTimes(1)
  })

  it('una carga inicial lenta no repone fuentes después de perder el acceso', async () => {
    const api = workspaceApi()
    const initial = deferred<SourceDocumentView[]>()
    vi.mocked(api.content.listSources).mockReturnValueOnce(initial.promise)
    vi.mocked(api.content.listSourceJobs).mockRejectedValueOnce(new ApiError('Acceso revocado', 403))
    const { result } = await mount(api)
    expect(result.current.sources).toEqual([])
    await act(async () => { initial.resolve([sourceDocument]) })
    expect(result.current.sources).toEqual([])
    expect(result.current.error).toBe('Acceso revocado')
    expect(result.current.sourcesLoaded).toBe(true)
  })

  it('la carga inicial no sobrescribe una versión más reciente del seguimiento', async () => {
    const api = workspaceApi()
    const initial = deferred<SourceDocumentView[]>()
    const published = { ...sourceDocument, versionId: 'version-2', versionNumber: 2 }
    vi.mocked(api.content.listSources).mockReturnValueOnce(initial.promise).mockResolvedValue([published])
    const { result } = await mount(api)
    expect(result.current.sources).toEqual([published])
    await act(async () => { initial.resolve([sourceDocument]) })
    expect(result.current.sources).toEqual([published])
    expect(result.current.sourcesLoaded).toBe(true)
  })

  it('ignora un error tardío de una consulta cancelada al salir del archivo', async () => {
    const api = workspaceApi()
    const pending = deferred<SourceJobView[]>()
    vi.mocked(api.content.listSourceJobs).mockReturnValueOnce(pending.promise)
    const { result, rerender } = await mount(api)
    rerender({ visible: false })
    await act(async () => { pending.reject(new Error('Respuesta anterior')) })
    expect(result.current.error).toBeNull()
    expect(vi.getTimerCount()).toBe(0)
  })
})

describe('workspace: recuperación y mutaciones', () => {
  it.each(['replace', 'reprocess', 'upload'] as const)('mantiene el estado ocupado y refresca tras %s', async (operation) => {
    const api = workspaceApi()
    const pending = deferred<SourceSubmission>()
    const method = operation === 'replace' ? api.content.replaceSource
      : operation === 'reprocess' ? api.content.reprocessSource : api.content.uploadSource
    vi.mocked(method).mockReturnValueOnce(pending.promise)
    const { result } = await mount(api)
    const file = new File(['Crónica nueva'], 'cronica.md')
    let completed!: Promise<boolean>
    act(() => {
      completed = operation === 'upload' ? result.current.uploadSource('Crónica', file)
        : result.current.recoverSource('source-1', 'public', operation === 'replace' ? file : undefined)
    })
    expect(result.current.uploading).toBe(true)
    expect(result.current.sources).toEqual([sourceDocument])
    if (operation === 'replace') expect(method).toHaveBeenCalledWith('realm-1', 'source-1', 'public', file)
    else if (operation === 'reprocess') expect(method).toHaveBeenCalledWith('realm-1', 'source-1')
    else expect(method).toHaveBeenCalledWith('realm-1', 'Crónica', 'public', file)
    await act(async () => { pending.resolve({ ...sourceSubmission, excludedSentences: 2 }); expect(await completed).toBe(true) })
    expect(result.current.uploading).toBe(false)
    expect(api.content.listSourceJobs).toHaveBeenCalledTimes(2)
    expect(result.current.sourceWarning).toContain('2 frase(s)')
  })

  it.each(['replace', 'reprocess', 'upload'] as const)('conserva las fuentes y permite continuar cuando falla %s', async (operation) => {
    const api = workspaceApi()
    const method = operation === 'replace' ? api.content.replaceSource
      : operation === 'reprocess' ? api.content.reprocessSource : api.content.uploadSource
    vi.mocked(method).mockRejectedValueOnce(new Error('No se pudo guardar'))
    const { result } = await mount(api)
    const file = new File(['Crónica'], 'cronica.md')
    await act(async () => {
      const completed = operation === 'upload' ? result.current.uploadSource('Crónica', file)
        : result.current.recoverSource('source-1', 'public', operation === 'replace' ? file : undefined)
      expect(await completed).toBe(false)
    })
    expect(result.current.uploading).toBe(false)
    expect(result.current.error).toBe('No se pudo guardar')
    expect(result.current.sources).toEqual([sourceDocument])
    expect(api.content.listSourceJobs).toHaveBeenCalledTimes(1)
  })

  it('no envía una subida sin política seleccionada', async () => {
    const api = workspaceApi()
    vi.mocked(api.realm.listPolicies).mockResolvedValue([])
    const { result } = await mount(api)
    await act(async () => { expect(await result.current.uploadSource('Crónica', new File(['texto'], 'c.md'))).toBe(false) })
    expect(api.content.uploadSource).not.toHaveBeenCalled()
    expect(result.current.uploading).toBe(false)
  })

  it('restablece el estado tras un reintento fallido y refresca al reintentar correctamente', async () => {
    const api = workspaceApi()
    const pending = deferred<SourceJobView>()
    vi.mocked(api.content.retrySourceJob).mockReturnValueOnce(pending.promise)
    const { result } = await mount(api)
    let completed!: Promise<void>
    act(() => { completed = result.current.retrySourceJob('job-1') })
    expect(result.current.uploading).toBe(true)
    await act(async () => { pending.reject(new Error('Archivo no disponible')); await completed })
    expect(result.current.error).toBe('Archivo no disponible')
    expect(result.current.uploading).toBe(false)
    expect(result.current.sources).toEqual([sourceDocument])
    expect(api.content.listSourceJobs).toHaveBeenCalledTimes(1)
    await act(async () => { await result.current.retrySourceJob('job-1') })
    expect(api.content.retrySourceJob).toHaveBeenLastCalledWith('realm-1', 'job-1')
    expect(result.current.error).toBeNull()
    expect(result.current.uploading).toBe(false)
    expect(api.content.listSourceJobs).toHaveBeenCalledTimes(2)
  })

  it('conserva la fuente si falla el borrado y la retira solo tras confirmación del servidor', async () => {
    const api = workspaceApi()
    vi.mocked(api.content.deleteSource).mockRejectedValueOnce(new Error('Borrado rechazado'))
    const { result } = await mount(api)
    await act(async () => { expect(await result.current.deleteSource(sourceDocument)).toBe(false) })
    expect(result.current.sources).toEqual([sourceDocument])
    expect(result.current.error).toBe('Borrado rechazado')
    await act(async () => { expect(await result.current.deleteSource(sourceDocument)).toBe(true) })
    expect(result.current.sources).toEqual([])
    expect(result.current.error).toBeNull()
  })
})
