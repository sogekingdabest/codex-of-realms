import { afterEach, describe, expect, it, vi } from 'vitest'

import { ApiError, AuthenticatedHttpClient } from '../shared/api'
import { createApiClients } from './ApiClients'

describe('feature API clients', () => {
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
    const api = createApiClients('/api/v1/', getToken)

    await api.qa.ask('realm 1', '¿Qué protege la Aguja?')

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
    const api = createApiClients('/api/v1', async () => 'token')
    const file = new File(['# Lumbrevela'], 'lumbrevela.md', {
      type: 'text/markdown',
    })

    await api.content.uploadSource('realm-1', 'Lumbrevela', 'policy-1', file)

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = new Headers(init.headers)
    expect(headers.has('Content-Type')).toBe(false)
    expect(init.body).toBeInstanceOf(FormData)
    const body = init.body as FormData
    expect(body.get('title')).toBe('Lumbrevela')
    expect(body.get('accessPolicyId')).toBe('policy-1')
    expect(body.get('file')).toBe(file)
  })

  it('propaga AbortSignal en las lecturas cancelables', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ user: {}, realms: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const api = createApiClients('/api/v1', async () => 'token')
    const controller = new AbortController()

    await api.realm.getCurrentUser(controller.signal)

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.signal).toBe(controller.signal)
  })

  it('acepta respuestas vacías en revocaciones y borrados', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    const api = createApiClients('/api/v1', async () => 'token')

    await expect(api.realm.revokeInvitation('realm-1', 'invite-1')).resolves.toBeUndefined()
  })

  it('expone el código estable de los errores ProblemDetail', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          type: 'urn:codex-of-realms:problem:invitation.pending_exists',
          title: 'Invitation conflict',
          status: 409,
          detail: 'A pending invitation already exists for this email.',
          code: 'invitation.pending_exists',
        }),
        { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    )
    vi.stubGlobal('fetch', fetchMock)
    const api = createApiClients('/api/v1', async () => 'token')

    const error = await api.realm.inviteMember('realm-1', 'player@example.test', 'PLAYER')
      .catch((reason: unknown) => reason)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      message: 'A pending invitation already exists for this email.',
      status: 409,
      code: 'invitation.pending_exists',
    })
  })

  it('tolera errores antiguos o no JSON sin código', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('upstream failure', { status: 502 }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const api = createApiClients('/api/v1', async () => 'token')

    const error = await api.realm.getCurrentUser().catch((reason: unknown) => reason)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ message: 'Error HTTP 502', status: 502, code: null })
  })

  it('conserva los códigos de conflicto del catálogo de lore', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          type: 'urn:codex-of-realms:problem:lore_entity.active_relations',
          title: 'Lore entity conflict',
          status: 409,
          detail: "Delete the entity's active relations before deleting the entity.",
          code: 'lore_entity.active_relations',
        }),
        { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    )
    vi.stubGlobal('fetch', fetchMock)
    const api = createApiClients('/api/v1', async () => 'token')

    const error = await api.lore.deleteLoreEntity('realm-1', 'entity-1')
      .catch((reason: unknown) => reason)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 409,
      code: 'lore_entity.active_relations',
    })
  })

  it('envía las fichas del canon con evidencia como JSON tipado', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: 'entity-1' }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const api = createApiClients('/api/v1', async () => 'token')
    const input = {
      type: 'CHARACTER' as const,
      displayName: 'Nara Vey',
      aliases: ['La Cartógrafa'],
      description: 'Custodia el paso oriental.',
      accessPolicyId: 'policy-1',
      evidenceChunkIds: ['chunk-1'],
    }

    await api.lore.createLoreEntity('realm 1', input)

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
    const api = createApiClients('/api/v1', async () => 'token')

    await api.qa.ask('../../outside?redirect=https://example.test', 'pregunta')

    const [url] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(
      '/api/v1/realms/..%2F..%2Foutside%3Fredirect%3Dhttps%3A%2F%2Fexample.test/questions',
    )
  })

  it('conserva el origen permitido cuando la API usa una URL absoluta', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ outcome: 'INSUFFICIENT_EVIDENCE' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const api = createApiClients('https://api.example.test/api/v1', async () => 'token')

    await api.qa.ask('realm-1', 'pregunta')

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.test/api/v1/realms/realm-1/questions',
      expect.any(Object),
    )
  })

  it('rechaza rutas absolutas o que escapen de la base configurada', async () => {
    const api = new AuthenticatedHttpClient('/api/v1', async () => 'token')

    await expect(api.request('//evil.example/path')).rejects.toThrow('La ruta solicitada no es válida.')
    await expect(api.request('/../outside')).rejects.toThrow(
      'La ruta solicitada queda fuera de la API configurada.',
    )
  })
})
