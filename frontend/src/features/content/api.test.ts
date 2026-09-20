import { afterEach, describe, expect, it, vi } from 'vitest'

import { HttpContentApi } from './api'
import { ApiError, AuthenticatedHttpClient } from '../../shared/api'
import { sourceSubmission } from '../../test/contentApi'

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

function client(status = 202, body: unknown = sourceSubmission) {
  const fetchMock = vi.fn<typeof fetch>().mockImplementation(async () => new Response(
    status === 204 ? null : JSON.stringify(body), { status },
  ))
  vi.stubGlobal('fetch', fetchMock)
  const api = new HttpContentApi(new AuthenticatedHttpClient('/api/v1', async () => 'token'))
  return { api, fetchMock }
}

describe('contrato HTTP de contenido', () => {
  it('reemplaza con multipart, claves explícitas y los identificadores codificados', async () => {
    const { api, fetchMock } = client()
    const file = new File(['Crónica'], 'cronica.md', { type: 'text/markdown' })
    await expect(api.replaceSource('realm 1', 'doc/1', 'policy', file, 'replace-key')).resolves.toEqual(sourceSubmission)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/realms/realm%201/sources/doc%2F1')
    expect(init?.method).toBe('PUT')
    const headers = new Headers(init?.headers)
    expect(headers.get('Idempotency-Key')).toBe('replace-key')
    expect(headers.get('Authorization')).toBe('Bearer token')
    expect(headers.has('Content-Type')).toBe(false)
    expect(init?.body).toBeInstanceOf(FormData)
    const form = init?.body as FormData
    expect(form.get('accessPolicyId')).toBe('policy')
    expect(form.get('file')).toBe(file)
    expect(form.has('title')).toBe(false)
  })

  it('respeta una clave explícita de subida y conserva la respuesta sin cambios HTTP 200', async () => {
    const unchanged = { ...sourceSubmission, job: { ...sourceSubmission.job, state: 'SUCCEEDED', noOp: true } }
    const { api, fetchMock } = client(200, unchanged)
    await expect(api.uploadSource('realm', 'Crónica', 'policy', new File(['texto'], 'c.md'), 'upload-key'))
      .resolves.toEqual(unchanged)
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get('Idempotency-Key')).toBe('upload-key')
  })

  it('reutiliza la clave de reemplazo solo para el mismo archivo y ámbito', async () => {
    const { api, fetchMock } = client()
    const file = new File(['texto'], 'c.md')
    await api.replaceSource('realm', 'doc', 'policy', file)
    await api.replaceSource('realm', 'doc', 'policy', file)
    await api.replaceSource('realm-2', 'doc', 'policy', file)
    await api.replaceSource('realm', 'doc-2', 'policy', file)
    await api.replaceSource('realm', 'doc', 'policy-2', file)
    await api.replaceSource('realm', 'doc', 'policy', new File(['texto'], 'c.md'))
    const keys = fetchMock.mock.calls.map(([, init]) => new Headers(init?.headers).get('Idempotency-Key'))
    expect(keys[0]).toBeTruthy()
    expect(keys[1]).toBe(keys[0])
    expect(new Set([keys[0], ...keys.slice(2)]).size).toBe(5)
  })

  it('separa subida y reemplazo aunque coincidan el título y el ID del documento', async () => {
    const { api, fetchMock } = client()
    const file = new File(['texto'], 'c.md')
    await api.uploadSource('realm', 'doc', 'policy', file)
    await api.replaceSource('realm', 'doc', 'policy', file)
    const keys = fetchMock.mock.calls.map(([, init]) => new Headers(init?.headers).get('Idempotency-Key'))
    expect(keys[0]).not.toBe(keys[1])
  })

  it('conserva la clave de subida cuando se reintenta tras perder la respuesta', async () => {
    const { api, fetchMock } = client()
    fetchMock.mockRejectedValueOnce(new TypeError('Network error'))
    const file = new File(['texto'], 'c.md')
    await expect(api.uploadSource('realm', 'Crónica', 'policy', file)).rejects.toThrow('Network error')
    await expect(api.uploadSource('realm', 'Crónica', 'policy', file)).resolves.toEqual(sourceSubmission)
    const keys = fetchMock.mock.calls.map(([, init]) => new Headers(init?.headers).get('Idempotency-Key'))
    expect(keys[0]).toBe(keys[1])
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('permite repetir un reprocesamiento con clave explícita y distingue operaciones nuevas', async () => {
    const { api, fetchMock } = client()
    await api.reprocessSource('realm', 'doc', 'same-operation')
    await api.reprocessSource('realm', 'doc', 'same-operation')
    await api.reprocessSource('realm', 'doc')
    await api.reprocessSource('realm', 'doc')
    const keys = fetchMock.mock.calls.map(([, init]) => new Headers(init?.headers).get('Idempotency-Key'))
    expect(keys.slice(0, 2)).toEqual(['same-operation', 'same-operation'])
    expect(keys[2]).toBeTruthy()
    expect(keys[2]).not.toBe(keys[3])
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/realms/realm/sources/doc/reprocess')
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'POST' })
    expect(fetchMock.mock.calls[0][1]?.body).toBeUndefined()
  })

  it('propaga la cancelación y el ámbito de todas las lecturas de contenido', async () => {
    const { api, fetchMock } = client(200, [])
    const controller = new AbortController()
    await api.listSources('realm 1', controller.signal)
    await api.listSourceJobs('realm 1', controller.signal)
    await api.getSourceJob('realm 1', 'job/1', controller.signal)
    await api.getSourceContent('realm 1', 'doc/1', 'v/1', controller.signal)
    await api.listSourceChunks('realm 1', 'doc/1', controller.signal)
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/v1/realms/realm%201/sources', '/api/v1/realms/realm%201/source-jobs',
      '/api/v1/realms/realm%201/source-jobs/job%2F1',
      '/api/v1/realms/realm%201/sources/doc%2F1/versions/v%2F1/content',
      '/api/v1/realms/realm%201/sources/doc%2F1/chunks',
    ])
    for (const [, init] of fetchMock.mock.calls) expect(init?.signal).toBe(controller.signal)
  })

  it('propaga AbortError sin convertirlo en un fallo HTTP', async () => {
    const { api, fetchMock } = client()
    const aborted = new DOMException('Cancelado', 'AbortError')
    fetchMock.mockRejectedValueOnce(aborted)
    await expect(api.listSourceJobs('realm')).rejects.toBe(aborted)
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('reintenta el trabajo solicitado y admite el borrado sin cuerpo', async () => {
    const { api, fetchMock } = client(200, sourceSubmission.job)
    await expect(api.retrySourceJob('realm', 'job/1')).resolves.toEqual(sourceSubmission.job)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/realms/realm/source-jobs/job%2F1/retry')
    expect(fetchMock.mock.calls[0][1]?.method).toBe('POST')
    fetchMock.mockImplementationOnce(async () => new Response(null, { status: 204 }))
    await expect(api.deleteSource('realm', 'doc/1')).resolves.toBeUndefined()
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/realms/realm/sources/doc%2F1')
    expect(fetchMock.mock.calls[1][1]?.method).toBe('DELETE')
  })

  it.each([
    [409, 'source.idempotency_conflict'], [425, 'source.upload_in_progress'], [503, 'source.file_unavailable'],
  ])('conserva el error HTTP %i y su código para el consumidor', async (status, code) => {
    const { api } = client(status as number, { detail: 'Operación no completada', code })
    const error: unknown = await api.reprocessSource('realm', 'doc', 'key').catch((reason: unknown) => reason)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status, code, message: 'Operación no completada' })
  })
})
