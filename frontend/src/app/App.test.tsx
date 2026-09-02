import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { App } from './App'
import type { LoreAnswer } from '../features/qa'
import type { AuthSession } from '../shared/auth'
import { composeTestApiClients } from '../test/apiClients'

function testApi() {
  const api = {
    getCurrentUser: vi.fn().mockResolvedValue({
      user: {
        id: 'user-1',
        issuer: 'http://localhost:8180/realms/codex-of-realms',
        subject: 'subject-1',
        displayName: 'Maestra del Meridiano',
        email: 'gm-demo@local.invalid',
      },
      realms: [{ id: 'realm-1', name: 'El Meridiano', role: 'OWNER' }],
    }),
    createRealm: vi.fn(),
    listPolicies: vi.fn().mockResolvedValue([
      { id: 'policy-1', realmId: 'realm-1', classification: 'PUBLIC', name: 'Público', description: null },
    ]),
    createPolicy: vi.fn(),
    listMemberships: vi.fn().mockResolvedValue([
      { userId: 'user-1', displayName: 'Maestra del Meridiano', email: 'gm-demo@local.invalid', role: 'OWNER' },
    ]),
    listInvitations: vi.fn().mockResolvedValue([]),
    inviteMember: vi.fn(),
    revokeInvitation: vi.fn(),
    removeMembership: vi.fn(),
    listPolicyGrants: vi.fn().mockResolvedValue([]),
    grantPolicy: vi.fn(),
    revokePolicy: vi.fn(),
    listSources: vi.fn().mockResolvedValue([
      {
        id: 'source-1',
        realmId: 'realm-1',
        title: 'Crónica de Lumbrevela',
        versionId: 'version-1',
        versionNumber: 2,
        checksumSha256: 'checksum',
        originalFilename: 'lumbrevela.md',
        mediaType: 'text/markdown',
        language: 'es',
        status: 'READY',
        accessPolicyId: 'policy-1',
        embeddingProvider: 'ollama',
        embeddingModel: 'bge-m3',
        embeddingDimension: 1024,
        chunkCount: 4,
        createdAt: '2026-08-28T10:00:00Z',
      },
    ]),
    uploadSource: vi.fn(),
    deleteSource: vi.fn(),
    getSourceContent: vi.fn().mockResolvedValue({
      documentId: 'source-1',
      versionId: 'version-1',
      title: 'Crónica de Lumbrevela',
      originalFilename: 'lumbrevela.md',
      content: '# La Aguja\nLa Aguja conserva una deuda antigua con el Meridiano.',
    }),
    listSourceChunks: vi.fn().mockResolvedValue([]),
    listLoreEntities: vi.fn().mockResolvedValue([]),
    createLoreEntity: vi.fn(),
    updateLoreEntity: vi.fn(),
    promoteLoreEntity: vi.fn(),
    deleteLoreEntity: vi.fn(),
    listLoreRelations: vi.fn().mockResolvedValue([]),
    createLoreRelation: vi.fn(),
    updateLoreRelation: vi.fn(),
    promoteLoreRelation: vi.fn(),
    deleteLoreRelation: vi.fn(),
    ask: vi.fn().mockResolvedValue({
      outcome: 'ANSWERED',
      answer: 'La Aguja conserva una deuda antigua con el Meridiano.',
      citations: [
        {
          rank: 1,
          realmId: 'realm-1',
          chunkId: 'chunk-1',
          sourceDocumentId: 'source-1',
          documentVersionId: 'version-1',
          versionNumber: 2,
          sourceTitle: 'Crónica de Lumbrevela',
          heading: 'La Aguja',
          startOffset: 40,
          endOffset: 126,
        },
      ],
      provenance: {
        embeddingProvider: 'ollama',
        embeddingModel: 'bge-m3',
        chatProvider: 'ollama',
        chatModel: 'qwen3.5:4b',
      },
      failureReason: null,
    }),
    getCapabilities: vi.fn().mockResolvedValue({
      chat: { provider: 'ollama', model: 'qwen3.5:4b', available: true, status: 'READY', installedModels: [] },
      embedding: { provider: 'ollama', model: 'bge-m3', available: true, status: 'READY', installedModels: [] },
    }),
  }
  return composeTestApiClients(api)
}

const session: AuthSession = {
  displayName: 'Maestra',
  getAccessToken: vi.fn().mockResolvedValue('token'),
  logout: vi.fn().mockResolvedValue(undefined),
}

describe('App', () => {
  it('mantiene la biblioteca disponible mientras se preparan las políticas base', async () => {
    const api = testApi()
    vi.mocked(api.listPolicies).mockResolvedValue([])

    render(<App api={api} session={session} />)

    expect(await screen.findByText('Crónica de Lumbrevela')).toBeInTheDocument()
    expect(screen.getByText('Preparando las políticas base del universo…')).toBeInTheDocument()
  })

  it('carga el realm y presenta sus fuentes visibles', async () => {
    render(<App api={testApi()} session={session} />)

    expect(await screen.findByText('El Meridiano')).toBeInTheDocument()
    expect(await screen.findByText('Crónica de Lumbrevela')).toBeInTheDocument()
    expect(screen.getByText('lumbrevela.md · versión 2')).toBeInTheDocument()
    expect(screen.getByText('4 fragmentos')).toBeInTheDocument()
  })

  it('remonta el workspace al cambiar de realm y aborta la carga anterior', async () => {
    const api = testApi()
    const [source] = await api.listSources('realm-1')
    if (!source) throw new Error('La fuente de prueba es obligatoria')
    const signals: AbortSignal[] = []
    vi.mocked(api.listSources).mockClear()
    vi.mocked(api.listSources).mockImplementation(async (realmId, signal) => {
      if (signal) signals.push(signal)
      return [{
        ...source,
        id: `source-${realmId}`,
        realmId,
        title: realmId === 'realm-1' ? 'Crónica del Meridiano' : 'Crónica de la Frontera',
      }]
    })
    vi.mocked(api.getCurrentUser).mockResolvedValue({
      user: {
        id: 'user-1', issuer: 'issuer', subject: 'subject-1', displayName: 'Maestra', email: null,
      },
      realms: [
        { id: 'realm-1', name: 'El Meridiano', role: 'OWNER' },
        { id: 'realm-2', name: 'La Frontera', role: 'OWNER' },
      ],
    })

    render(<App api={api} session={session} />)
    expect(await screen.findByText('Crónica del Meridiano')).toBeInTheDocument()
    fireEvent.change(screen.getByDisplayValue('El Meridiano'), { target: { value: 'realm-2' } })

    expect(await screen.findByText('Crónica de la Frontera')).toBeInTheDocument()
    expect(api.listSources).toHaveBeenCalledWith('realm-2', expect.any(AbortSignal))
    expect(signals[0]?.aborted).toBe(true)
  })

  it('mantiene las fuentes visibles si falla la carga independiente de acceso', async () => {
    const api = testApi()
    vi.mocked(api.listPolicies).mockRejectedValue(new Error('Acceso temporalmente no disponible'))

    render(<App api={api} session={session} />)

    expect(await screen.findByText('Crónica de Lumbrevela')).toBeInTheDocument()
    expect(await screen.findByText('Acceso temporalmente no disponible')).toBeInTheDocument()
  })

  it('muestra una respuesta con la localización exacta de la cita', async () => {
    const api = testApi()
    render(<App api={api} session={session} />)
    await screen.findByText('Crónica de Lumbrevela')

    fireEvent.change(screen.getByLabelText('¿Qué quieres saber?'), {
      target: { value: '¿Qué deuda conserva la Aguja?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Consultar' }))

    expect(
      await screen.findByText('La Aguja conserva una deuda antigua con el Meridiano.'),
    ).toBeInTheDocument()
    expect(screen.getByText(/La Aguja · v2 · caracteres 40–126/)).toBeInTheDocument()
    expect(api.ask).toHaveBeenCalledWith('realm-1', '¿Qué deuda conserva la Aguja?')

    fireEvent.click(screen.getByRole('button', { name: 'Abrir evidencia exacta' }))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(api.getSourceContent).toHaveBeenCalledWith('realm-1', 'source-1', 'version-1')
  })

  it('permite que un jugador cargue sus fuentes visibles y pregunte sin pedir políticas', async () => {
    const api = testApi()
    vi.mocked(api.getCurrentUser).mockResolvedValue({
      user: {
        id: 'player-1',
        issuer: 'http://localhost:8180/realms/codex-of-realms',
        subject: 'player-subject',
        displayName: 'Nara Valcor',
        email: 'nara-demo@local.invalid',
      },
      realms: [{ id: 'realm-1', name: 'El Meridiano', role: 'PLAYER' }],
    })

    render(<App api={api} session={session} />)

    expect(await screen.findByText('Crónica de Lumbrevela')).toBeInTheDocument()
    expect(api.listSources).toHaveBeenCalledWith('realm-1', expect.any(AbortSignal))
    expect(api.listPolicies).not.toHaveBeenCalled()
    expect(api.listMemberships).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Consultar' })).toBeInTheDocument()
  })

  it('abre el atlas del canon desde la navegación principal', async () => {
    const api = testApi()
    render(<App api={api} session={session} />)
    await screen.findByText('Crónica de Lumbrevela')

    fireEvent.click(screen.getByRole('button', { name: 'Atlas del canon' }))

    expect(await screen.findByRole('heading', { name: 'Atlas del canon' })).toBeInTheDocument()
    expect(api.listLoreEntities).toHaveBeenCalledWith('realm-1', expect.any(AbortSignal))
    expect(api.listLoreRelations).toHaveBeenCalledWith('realm-1', expect.any(AbortSignal))
  })

  it('crea el primer universo desde el estado vacío', async () => {
    const api = testApi()
    vi.mocked(api.getCurrentUser).mockResolvedValue({
      user: {
        id: 'user-1',
        issuer: 'http://localhost:8180/realms/codex-of-realms',
        subject: 'subject-1',
        displayName: 'Maestra del Meridiano',
        email: 'gm-demo@local.invalid',
      },
      realms: [],
    })
    vi.mocked(api.createRealm).mockResolvedValue({
      id: 'realm-new',
      name: 'Nueva Frontera',
      role: 'OWNER',
    })

    render(<App api={api} session={session} />)
    fireEvent.change(await screen.findByLabelText('Nombre del universo'), {
      target: { value: 'Nueva Frontera' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Crear universo' }))

    await waitFor(() => expect(api.createRealm).toHaveBeenCalledWith('Nueva Frontera'))
    expect(await screen.findByText('Nueva Frontera')).toBeInTheDocument()
  })

  it('muestra el runtime incompleto y una respuesta segura sin modelo', async () => {
    const api = testApi()
    vi.mocked(api.getCapabilities).mockResolvedValue({
      chat: { provider: 'ollama', model: 'qwen3.5:4b', available: false, status: 'MODEL_MISSING', installedModels: [] },
      embedding: { provider: 'ollama', model: 'bge-m3', available: false, status: 'MODEL_MISSING', installedModels: [] },
    })
    vi.mocked(api.ask).mockResolvedValue({
      outcome: 'INSUFFICIENT_EVIDENCE',
      answer: null,
      citations: [],
      provenance: {
        embeddingProvider: 'ollama',
        embeddingModel: 'bge-m3',
        chatProvider: 'ollama',
        chatModel: 'qwen3.5:4b',
      },
      failureReason: 'MODEL_UNAVAILABLE',
    })

    render(<App api={api} session={session} />)
    expect(await screen.findByText('El runtime local necesita preparación')).toBeInTheDocument()
    expect(screen.getByText('ollama pull bge-m3')).toBeInTheDocument()
    expect(screen.getByText('ollama pull qwen3.5:4b')).toBeInTheDocument()
    await screen.findByText('Crónica de Lumbrevela')
    fireEvent.change(screen.getByLabelText('¿Qué quieres saber?'), {
      target: { value: '¿Qué ocurrió?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Consultar' }))

    expect(await screen.findByText('No se pudo consultar el modelo')).toBeInTheDocument()
    expect(screen.getByText('Runtime no disponible')).toBeInTheDocument()
  })

  it('explica que Ollama no responde sin recomendar descargar modelos', async () => {
    const api = testApi()
    vi.mocked(api.getCapabilities).mockResolvedValue({
      chat: { provider: 'ollama', model: 'qwen3.5:4b', available: false, status: 'RUNTIME_UNAVAILABLE', installedModels: [] },
      embedding: { provider: 'ollama', model: 'bge-m3', available: false, status: 'RUNTIME_UNAVAILABLE', installedModels: [] },
    })

    render(<App api={api} session={session} />)

    expect(await screen.findByText('Ollama no responde. Inicia o revisa el runtime local.')).toBeInTheDocument()
    expect(screen.queryByText(/ollama pull/)).not.toBeInTheDocument()
  })

  it('explica una configuración incompleta sin mostrar comandos engañosos', async () => {
    const api = testApi()
    vi.mocked(api.getCapabilities).mockResolvedValue({
      chat: { provider: 'none', model: 'qwen3.5:4b', available: false, status: 'NOT_CONFIGURED', installedModels: [] },
      embedding: { provider: 'none', model: 'bge-m3', available: false, status: 'NOT_CONFIGURED', installedModels: [] },
    })

    render(<App api={api} session={session} />)

    expect(await screen.findByText(
      'Configura el proveedor y el modelo correspondientes antes de continuar.',
    )).toBeInTheDocument()
    expect(screen.queryByText(/ollama pull/)).not.toBeInTheDocument()
  })

  it('resuelve estados mixtos por capacidad y evita comandos duplicados', async () => {
    const api = testApi()
    vi.mocked(api.getCapabilities).mockResolvedValue({
      chat: { provider: 'none', model: 'shared-model', available: false, status: 'NOT_CONFIGURED', installedModels: [] },
      embedding: { provider: 'ollama', model: 'shared-model', available: false, status: 'MODEL_MISSING', installedModels: [] },
    })

    render(<App api={api} session={session} />)

    expect(await screen.findByText('Respuestas: shared-model (sin configurar).')).toBeInTheDocument()
    expect(screen.getAllByText('ollama pull shared-model')).toHaveLength(1)
    expect(screen.getByText(
      'Configura el proveedor y el modelo correspondientes antes de continuar.',
    )).toBeInTheDocument()
  })

  it.each([
    ['NO_EVIDENCE', 'El archivo no contiene información visible que permita responder con garantías.'],
    ['LOW_RELEVANCE', 'Hay contenido relacionado, pero no es suficientemente preciso para sostener una respuesta.'],
    ['UNSAFE_INPUT', 'La consulta o la evidencia contiene instrucciones inseguras y se ha rechazado.'],
    ['MODEL_UNAVAILABLE', 'El modelo de respuesta no está disponible. Revisa el estado del runtime local.'],
    ['VALIDATION_FAILED', 'El modelo respondió, pero la respuesta no superó la validación de evidencia y citas.'],
  ] satisfies Array<[NonNullable<LoreAnswer['failureReason']>, string]>) (
    'presenta el motivo seguro %s devuelto por qa',
    async (failureReason, expectedMessage) => {
      const api = testApi()
      vi.mocked(api.ask).mockResolvedValue({
        outcome: 'INSUFFICIENT_EVIDENCE',
        answer: null,
        citations: [],
        provenance: {
          embeddingProvider: 'ollama',
          embeddingModel: 'bge-m3',
          chatProvider: 'ollama',
          chatModel: 'qwen3.5:4b',
        },
        failureReason,
      })

      render(<App api={api} session={session} />)
      await screen.findByText('Crónica de Lumbrevela')
      fireEvent.change(screen.getByLabelText('¿Qué quieres saber?'), {
        target: { value: '¿Qué ocurrió?' },
      })
      fireEvent.click(screen.getByRole('button', { name: 'Consultar' }))

      expect(await screen.findByText(expectedMessage)).toBeInTheDocument()
    },
  )

  it('sube y elimina fuentes, incluyendo estados fallido y en proceso', async () => {
    const api = testApi()
    const [baseSource] = await api.listSources('realm-1')
    if (!baseSource) throw new Error('La fuente de prueba es obligatoria')
    vi.mocked(api.listSources).mockResolvedValue([
      { ...baseSource, id: 'failed', title: 'Fuente fallida', status: 'FAILED' },
      { ...baseSource, id: 'processing', title: 'Fuente en proceso', status: 'PROCESSING' },
    ])
    vi.mocked(api.uploadSource).mockResolvedValue({ ...baseSource, id: 'uploaded', title: 'Nueva fuente' })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)

    render(<App api={api} session={session} />)
    expect(await screen.findByText('Fuente fallida')).toBeInTheDocument()
    expect(screen.getByText('Fallida')).toBeInTheDocument()
    expect(screen.getByText('Procesando')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Título'), { target: { value: 'Nueva fuente' } })
    fireEvent.change(screen.getByLabelText('Archivo Markdown o TXT'), {
      target: { files: [new File(['contenido'], 'nueva.md', { type: 'text/markdown' })] },
    })
    fireEvent.submit(screen.getByRole('button', { name: 'Subir y procesar' }).closest('form')!)
    await waitFor(() => expect(api.uploadSource).toHaveBeenCalled())
    fireEvent.click(screen.getAllByRole('button', { name: 'Eliminar' })[0])
    await waitFor(() => expect(api.deleteSource).toHaveBeenCalledWith('realm-1', 'uploaded'))
    confirm.mockRestore()
  })

  it('presenta y permite cerrar errores de operación', async () => {
    const api = testApi()
    vi.mocked(api.ask).mockRejectedValue(new Error('Servicio temporalmente no disponible'))
    render(<App api={api} session={session} />)
    await screen.findByText('Crónica de Lumbrevela')

    fireEvent.change(screen.getByLabelText('¿Qué quieres saber?'), { target: { value: 'pregunta' } })
    fireEvent.click(screen.getByRole('button', { name: 'Consultar' }))
    expect(await screen.findByText('Servicio temporalmente no disponible')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar aviso' }))
    expect(screen.queryByText('Servicio temporalmente no disponible')).not.toBeInTheDocument()
  })
})
