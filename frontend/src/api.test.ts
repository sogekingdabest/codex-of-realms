import { afterEach, describe, expect, it, vi } from 'vitest'

import { HttpCodexApi } from './api'

describe('HttpCodexApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renueva el token y envía preguntas como JSON autenticado', async () => {
    const getToken = vi.fn().mockResolvedValue('fresh-token')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          outcome: 'INSUFFICIENT_EVIDENCE',
          answer: null,
          citations: [],
          provenance: {
            embeddingProvider: 'ollama',
            embeddingModel: 'bge-m3',
            chatProvider: 'ollama',
            chatModel: 'qwen3.5:4b',
          },
          failureReason: 'NO_EVIDENCE',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    vi.stubGlobal('fetch', fetchMock)
    const api = new HttpCodexApi('/api/v1/', getToken)

    await api.ask('realm 1', '¿Qué protege la Aguja?')

    expect(getToken).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/realms/realm%201/questions')
    expect(init.method).toBe('POST')
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer fresh-token')
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
    expect(init.body).toBe(JSON.stringify({ question: '¿Qué protege la Aguja?' }))
  })

  it('deja que el navegador construya el content type multipart', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: 'source-1' }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const api = new HttpCodexApi('/api/v1', async () => 'token')
    const file = new File(['# Lumbrevela'], 'lumbrevela.md', {
      type: 'text/markdown',
    })

    await api.uploadSource('realm-1', 'Lumbrevela', 'policy-1', file)

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = new Headers(init.headers)
    expect(headers.has('Content-Type')).toBe(false)
    expect(init.body).toBeInstanceOf(FormData)
    const body = init.body as FormData
    expect(body.get('title')).toBe('Lumbrevela')
    expect(body.get('accessPolicyId')).toBe('policy-1')
    expect(body.get('file')).toBe(file)
  })

  it('acepta respuestas vacías en revocaciones y borrados', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    const api = new HttpCodexApi('/api/v1', async () => 'token')

    await expect(api.revokeInvitation('realm-1', 'invite-1')).resolves.toBeUndefined()
  })

  it('envía las fichas del canon con evidencia como JSON tipado', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: 'entity-1' }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const api = new HttpCodexApi('/api/v1', async () => 'token')
    const input = {
      type: 'CHARACTER' as const,
      displayName: 'Nara Vey',
      aliases: ['La Cartógrafa'],
      description: 'Custodia el paso oriental.',
      accessPolicyId: 'policy-1',
      evidenceChunkIds: ['chunk-1'],
    }

    await api.createLoreEntity('realm 1', input)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/realms/realm%201/catalogue/entities')
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify(input))
  })

  it('mantiene los identificadores no confiables dentro de la ruta configurada', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ outcome: 'INSUFFICIENT_EVIDENCE' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const api = new HttpCodexApi('/api/v1', async () => 'token')

    await api.ask('../../outside?redirect=https://example.test', 'pregunta')

    const [url] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(
      '/api/v1/realms/..%2F..%2Foutside%3Fredirect%3Dhttps%3A%2F%2Fexample.test/questions',
    )
  })
})
