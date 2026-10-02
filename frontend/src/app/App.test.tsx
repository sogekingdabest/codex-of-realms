import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, onTestFinished, vi } from 'vitest'

import { App } from './App'
import type { LoreAnswer } from '../features/qa'
import type { AuthSession } from '../shared/auth'
import { composeTestApiClients } from '../test/apiClients'
import { sourceJob } from '../test/contentApi'

function testApi() {
  const api = {
    getCurrentUser: vi.fn().mockResolvedValue({
      user: {
        id: 'user-1',
        issuer: 'http://localhost:8180/realms/codex-of-realms',
        subject: 'subject-1', emailVerified: true,
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
        visibility: 'PUBLIC',
        embeddingProvider: 'ollama',
        embeddingModel: 'bge-m3',
        embeddingDimension: 1024,
        chunkCount: 4,
        createdAt: '2026-08-28T10:00:00Z',
      },
    ]),
    uploadSource: vi.fn(),
    replaceSource: vi.fn(),
    reprocessSource: vi.fn(),
    listSourceJobs: vi.fn().mockResolvedValue([]),
    getSourceJob: vi.fn(),
    retrySourceJob: vi.fn(),
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
      outcome: 'ANSWERED', answerMode: 'EXTRACTIVE',
      excerpts: [{ text: 'La Aguja conserva una deuda antigua con el Meridiano.', citationRank: 1 }],
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
    warmUp: vi.fn().mockResolvedValue({ state: 'STARTED' }),
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

    expect(await screen.findByRole('heading', { name: 'El Meridiano' })).toBeInTheDocument()
    expect(await screen.findByText('Crónica de Lumbrevela')).toBeInTheDocument()
    expect(screen.getByText('lumbrevela.md · v2')).toBeInTheDocument()
    expect(screen.getByText('Público', { selector: '.visibility-mark' })).toBeInTheDocument()
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
        id: 'user-1', issuer: 'issuer', subject: 'subject-1', emailVerified: true, displayName: 'Maestra', email: null,
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
    fireEvent.click(screen.getByRole('link', { name: 'Consultas' }))

    fireEvent.change(screen.getByLabelText('¿Qué quieres saber?'), {
      target: { value: '¿Qué deuda conserva la Aguja?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Consultar' }))

    expect(
      await screen.findByText('La Aguja conserva una deuda antigua con el Meridiano.'),
    ).toBeInTheDocument()
    expect(screen.getByText(/La Aguja · v2 · caracteres 40–126/)).toBeInTheDocument()
    expect(api.ask).toHaveBeenCalledWith('realm-1', '¿Qué deuda conserva la Aguja?', expect.any(AbortSignal))

    fireEvent.click(screen.getByRole('button', { name: 'Abrir evidencia exacta' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('lumbrevela.md · La Aguja')).toBeInTheDocument()
    expect(api.getSourceContent).toHaveBeenCalledWith('realm-1', 'source-1', 'version-1', expect.any(AbortSignal))
  })

  it('carga los modelos al abrir Consultas, antes de la primera pregunta', async () => {
    const api = testApi()
    vi.mocked(api.warmUp).mockRejectedValue(new Error('runtime unreachable'))
    render(<App api={api} session={session} />)
    await screen.findByText('Crónica de Lumbrevela')
    expect(api.warmUp).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('link', { name: 'Consultas' }))

    await waitFor(() => expect(api.warmUp).toHaveBeenCalledOnce())
    // A failed warm-up is not an error for the person asking.
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('permite que un jugador cargue sus fuentes visibles y pregunte sin pedir políticas', async () => {
    const api = testApi()
    vi.mocked(api.getCurrentUser).mockResolvedValue({
      user: {
        id: 'player-1',
        issuer: 'http://localhost:8180/realms/codex-of-realms',
        subject: 'player-subject', emailVerified: true,
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
    expect(screen.queryByRole('link', { name: 'Personas y permisos' })).not.toBeInTheDocument()
    // A player finds the new realm button inside the realm switcher, not beside it.
    expect(screen.getByRole('button', { name: 'Nuevo universo' }).closest('details')).toHaveClass('realm-switcher')
    fireEvent.click(screen.getByRole('link', { name: 'Consultas' }))
    fireEvent.change(screen.getByLabelText('¿Qué quieres saber?'), { target: { value: '¿Qué deuda conserva la Aguja?' } })
    fireEvent.click(screen.getByRole('button', { name: 'Consultar' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Abrir evidencia exacta' }))

    // The evidence names the passage, not the uploaded file.
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('La Aguja', { selector: 'header span' })).toBeInTheDocument()
    expect(dialog).not.toHaveTextContent('lumbrevela.md')
  })

  it('abre el atlas del canon desde la navegación principal', async () => {
    const api = testApi()
    render(<App api={api} session={session} />)
    await screen.findByText('Crónica de Lumbrevela')

    fireEvent.click(screen.getByRole('link', { name: 'Atlas del canon' }))

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
        subject: 'subject-1', emailVerified: true,
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
    expect(await screen.findByRole('heading', { name: 'Nueva Frontera' })).toBeInTheDocument()
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
    await screen.findByText('Crónica de Lumbrevela')
    fireEvent.click(screen.getByRole('link', { name: 'Consultas' }))
    expect(await screen.findByText('El runtime local necesita preparación')).toBeInTheDocument()
    expect(api.warmUp).not.toHaveBeenCalled()
    expect(screen.getByText('ollama pull bge-m3')).toBeInTheDocument()
    expect(screen.getByText('ollama pull qwen3.5:4b')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('¿Qué quieres saber?'), {
      target: { value: '¿Qué ocurrió?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Consultar' }))

    expect(await screen.findByText('No se pudo consultar el modelo')).toBeInTheDocument()
    expect(screen.getByText('Modelo no disponible')).toBeInTheDocument()
  })

  it('explica que Ollama no responde sin recomendar descargar modelos', async () => {
    const api = testApi()
    vi.mocked(api.getCapabilities).mockResolvedValue({
      chat: { provider: 'ollama', model: 'qwen3.5:4b', available: false, status: 'RUNTIME_UNAVAILABLE', installedModels: [] },
      embedding: { provider: 'ollama', model: 'bge-m3', available: false, status: 'RUNTIME_UNAVAILABLE', installedModels: [] },
    })

    render(<App api={api} session={session} />)

    fireEvent.click(await screen.findByRole('link', { name: 'Consultas' }))
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

    fireEvent.click(await screen.findByRole('link', { name: 'Consultas' }))
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

    fireEvent.click(await screen.findByRole('link', { name: 'Consultas' }))
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
    ['MODEL_UNAVAILABLE', 'El modelo no respondió a tiempo o no está disponible. Si acaba de arrancar, puede seguir cargándose: vuelve a intentarlo en un minuto.'],
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
      fireEvent.click(screen.getByRole('link', { name: 'Consultas' }))
      fireEvent.change(screen.getByLabelText('¿Qué quieres saber?'), {
        target: { value: '¿Qué ocurrió?' },
      })
      fireEvent.click(screen.getByRole('button', { name: 'Consultar' }))

      expect(await screen.findByText(expectedMessage)).toBeInTheDocument()
    },
  )

  it('encola una subida y refresca las fuentes publicadas antes de eliminarlas', async () => {
    const api = testApi()
    const [baseSource] = await api.listSources('realm-1')
    if (!baseSource) throw new Error('Falta la fuente de prueba')
    const uploaded = { ...baseSource, id: 'uploaded', title: 'Nueva fuente' }
    vi.mocked(api.uploadSource).mockImplementation(async () => {
      vi.mocked(api.listSources).mockResolvedValue([uploaded])
      return { documentId: 'uploaded', versionId: 'v1', job: {} }
    })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<App api={api} session={session} />)
    await screen.findByText('Crónica de Lumbrevela')
    fireEvent.click(screen.getByText('Añadir conocimiento'))
    fireEvent.change(screen.getByLabelText('Título'), { target: { value: 'Nueva fuente' } })
    fireEvent.change(screen.getByLabelText('Archivo Markdown o TXT'), {
      target: { files: [new File(['contenido'], 'nueva.md', { type: 'text/markdown' })] },
    })
    fireEvent.submit(screen.getByRole('button', { name: 'Subir y procesar como Público' }).closest('form')!)
    await screen.findByText('Nueva fuente')
    fireEvent.click(screen.getByText('Gestionar'))
    fireEvent.click(screen.getByRole('button', { name: 'Eliminar' }))
    await waitFor(() => expect(api.deleteSource).toHaveBeenCalledWith('realm-1', 'uploaded'))
    confirm.mockRestore()
  })

  it('enseña a la dirección las primeras fuentes en proceso en vez de una biblioteca vacía', async () => {
    const api = testApi()
    vi.mocked(api.listSources).mockResolvedValue([])
    vi.mocked(api.listSourceJobs).mockResolvedValue([
      { ...sourceJob, id: 'job-a', documentId: 'doc-a', title: 'Rutas de Lumbrevela', state: 'RUNNING', completedChunks: 1, totalChunks: 4 },
      { ...sourceJob, id: 'job-b', documentId: 'doc-b', title: 'La campana de vidrio', state: 'QUEUED' },
    ])
    render(<App api={api} session={session} />)

    expect(await screen.findByRole('heading', { name: 'Tus primeras fuentes se están procesando' })).toBeInTheDocument()
    expect(screen.getByText('Procesando 2 documentos nuevos; aparecerán en la lista al terminar.')).toBeInTheDocument()
    const reading = screen.getByRole('main')
    expect(within(reading).getByText('Procesando · 1/4 fragmentos')).toBeInTheDocument()
    expect(within(reading).getByText('En cola · 0/2 fragmentos')).toBeInTheDocument()
    expect(screen.queryByText('Todavía no hay fuentes visibles en este universo.')).not.toBeInTheDocument()
    expect(screen.queryByText('El archivo empieza aquí')).not.toBeInTheDocument()
  })

  it('informa de una sustitución junto a la fuente que sustituye', async () => {
    const api = testApi()
    vi.mocked(api.listSourceJobs).mockResolvedValue([
      { ...sourceJob, documentId: 'source-1', versionId: 'version-3', versionNumber: 3, state: 'FAILED', completedChunks: 1, totalChunks: 4, attempts: 3 },
    ])
    render(<App api={api} session={session} />)

    const entry = (await screen.findByRole('link', { name: 'Leer Crónica de Lumbrevela' })).closest('article')!
    expect(await within(entry).findByText('Versión 3 · Necesita atención · 1/4 fragmentos · 3 intentos')).toBeInTheDocument()
    expect(within(entry).getByText('Revísala en «Procesamiento de fuentes». La versión 2 sigue publicada.')).toBeInTheDocument()
    for (const reprocess of screen.getAllByRole('button', { name: 'Reprocesar' })) expect(reprocess).toHaveClass('quiet-button')
  })

  it('avisa de una primera fuente que necesita atención', async () => {
    const api = testApi()
    vi.mocked(api.listSources).mockResolvedValue([])
    vi.mocked(api.listSourceJobs).mockResolvedValue([{ ...sourceJob, documentId: 'doc-a', state: 'FAILED', errorCode: 'MODEL_UNAVAILABLE' }])
    render(<App api={api} session={session} />)

    expect(await screen.findByRole('heading', { name: 'Una fuente necesita atención' })).toBeInTheDocument()
    expect(screen.getByText('1 documento nuevo necesita atención.')).toBeInTheDocument()
  })

  it('abre el formulario de subida de una biblioteca vacía desde que empieza a cargar', async () => {
    const api = testApi()
    let finishLoading!: (sources: Awaited<ReturnType<typeof api.listSources>>) => void
    vi.mocked(api.listSources).mockReturnValue(new Promise((resolve) => { finishLoading = resolve }))
    render(<App api={api} session={session} />)

    const form = (await screen.findByText('Añadir conocimiento')).closest('details')!
    expect(form).toHaveAttribute('open')
    await act(async () => finishLoading([]))
    expect(form).toHaveAttribute('open')
  })

  it('mantiene abierto el formulario de subida cuando se publica la primera fuente', async () => {
    const api = testApi()
    const [baseSource] = await api.listSources('realm-1')
    if (!baseSource) throw new Error('Falta la fuente de prueba')
    vi.mocked(api.listSources).mockResolvedValue([])
    vi.mocked(api.uploadSource).mockImplementation(async () => {
      vi.mocked(api.listSources).mockResolvedValue([{ ...baseSource, id: 'first', title: 'Primera fuente' }])
      return { documentId: 'first', versionId: 'v1', job: sourceJob }
    })
    render(<App api={api} session={session} />)
    const form = (await screen.findByText('Añadir conocimiento')).closest('details')!
    await waitFor(() => expect(form).toHaveAttribute('open'))

    fireEvent.change(screen.getByLabelText('Título'), { target: { value: 'Primera fuente' } })
    fireEvent.change(screen.getByLabelText('Archivo Markdown o TXT'), {
      target: { files: [new File(['contenido'], 'primera.md', { type: 'text/markdown' })] },
    })
    fireEvent.submit(screen.getByRole('button', { name: 'Subir y procesar como Público' }).closest('form')!)

    expect(await screen.findByRole('link', { name: 'Leer Primera fuente' })).toBeInTheDocument()
    expect(form).toHaveAttribute('open')
  })

  it('presenta y permite cerrar errores de operación', async () => {
    const api = testApi()
    vi.mocked(api.ask).mockRejectedValue(new Error('Servicio temporalmente no disponible'))
    render(<App api={api} session={session} />)
    await screen.findByText('Crónica de Lumbrevela')
    fireEvent.click(screen.getByRole('link', { name: 'Consultas' }))

    fireEvent.change(screen.getByLabelText('¿Qué quieres saber?'), { target: { value: 'pregunta' } })
    fireEvent.click(screen.getByRole('button', { name: 'Consultar' }))
    expect(await screen.findByText('Servicio temporalmente no disponible')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar aviso' }))
    expect(screen.queryByText('Servicio temporalmente no disponible')).not.toBeInTheDocument()
  })

  it('cancela una consulta que tarda sin mostrar un error y conserva la pregunta', async () => {
    const api = testApi()
    vi.mocked(api.ask).mockImplementation((_realmId, _question, signal) => new Promise((_resolve, reject) => {
      signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
    }))
    render(<App api={api} session={session} />)
    await screen.findByText('Crónica de Lumbrevela')
    fireEvent.click(screen.getByRole('link', { name: 'Consultas' }))
    fireEvent.change(screen.getByLabelText('¿Qué quieres saber?'), { target: { value: '¿Dónde se alza Lumbrevela?' } })
    fireEvent.click(screen.getByRole('button', { name: 'Consultar' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Contrastando fuentes y permisos…')
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(await screen.findByRole('button', { name: 'Consultar' })).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'Cancelar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByLabelText('¿Qué quieres saber?')).toHaveValue('¿Dónde se alza Lumbrevela?')
    expect(screen.getByLabelText('¿Qué quieres saber?')).toHaveFocus()
  })

  it('busca por título sin tildes y por archivo y abre la fuente sin preguntar a la IA', async () => {
    const api = testApi()
    render(<App api={api} session={session} />)
    const search = await screen.findByLabelText('Buscar fuentes')
    fireEvent.change(search, { target: { value: 'cronica' } })
    expect(screen.getByRole('link', { name: 'Leer Crónica de Lumbrevela' })).toBeVisible()
    fireEvent.change(search, { target: { value: 'inexistente' } })
    expect(screen.queryByRole('link', { name: 'Leer Crónica de Lumbrevela' })).not.toBeInTheDocument()
    fireEvent.change(search, { target: { value: 'lumbrevela.md' } })
    fireEvent.click(screen.getByRole('link', { name: 'Leer Crónica de Lumbrevela' }))
    expect(await screen.findByRole('article', { name: 'Fuente: Crónica de Lumbrevela' })).toHaveTextContent('La Aguja conserva una deuda antigua con el Meridiano.')
    // Focus moves in an effect after the reader appears.
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Crónica de Lumbrevela' })).toHaveFocus())
    expect(screen.getByRole('button', { name: 'Abrir índice' })).toHaveAttribute('aria-expanded', 'false')
    expect(api.ask).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar fuente' }))
    expect(screen.getByRole('button', { name: 'Cerrar índice' })).toHaveAttribute('aria-expanded', 'true')
    expect(screen.queryByRole('article', { name: 'Fuente: Crónica de Lumbrevela' })).not.toBeInTheDocument()
  })

  it('mantiene el universo actual si falla crear otro y permite reintentar', async () => {
    const api = testApi()
    vi.mocked(api.createRealm).mockRejectedValueOnce(new Error('No se pudo crear')).mockResolvedValueOnce({ id: 'realm-2', name: 'Frontera', role: 'OWNER' })
    render(<App api={api} session={session} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Nuevo universo' }))
    fireEvent.change(screen.getByLabelText('Nombre del universo'), { target: { value: 'Frontera' } })
    fireEvent.click(screen.getByRole('button', { name: 'Crear universo' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo crear')
    expect(screen.getByLabelText('Universo activo')).toHaveValue('realm-1')
    expect(screen.getByLabelText('Nombre del universo')).toHaveValue('Frontera')
    expect(screen.getByText('Crónica de Lumbrevela')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Crear universo' }))
    await waitFor(() => expect(screen.getByLabelText('Universo activo')).toHaveValue('realm-2'))
    expect(screen.queryByLabelText('Nombre del universo')).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Universo activo'), { target: { value: 'realm-1' } })
    expect(await screen.findByText('Crónica de Lumbrevela')).toBeVisible()
  })

  it('cancela la lectura anterior al cambiar de universo e ignora su respuesta tardía', async () => {
    const api = testApi()
    const user = await api.getCurrentUser()
    vi.mocked(api.getCurrentUser).mockResolvedValue({ ...user, realms: [...user.realms, { id: 'realm-2', name: 'Frontera', role: 'OWNER' }] })
    const content = await api.getSourceContent('realm-1', 'source-1', 'version-1')
    let finish!: (value: typeof content) => void
    vi.mocked(api.getSourceContent).mockClear().mockImplementation(() => new Promise((resolve) => { finish = resolve }))
    render(<App api={api} session={session} />)
    fireEvent.click(await screen.findByRole('link', { name: 'Leer Crónica de Lumbrevela' }))
    const signal = vi.mocked(api.getSourceContent).mock.calls[0][3]!
    fireEvent.change(screen.getByLabelText('Universo activo'), { target: { value: 'realm-2' } })
    expect(signal.aborted).toBe(true)
    await act(async () => { finish(content) })
    expect(screen.queryByRole('article', { name: 'Fuente: Crónica de Lumbrevela' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Universo activo')).toHaveValue('realm-2')
  })
})

describe('App: direcciones', () => {
  const entity = (id: string, displayName: string) => ({
    id, realmId: 'realm-1', type: 'PLACE', displayName, aliases: [], description: `${displayName} se alza al este.`,
    canonStatus: 'CANON', accessPolicyId: 'policy-1', visibility: 'PUBLIC', sourceEvidence: [], createdBy: 'user-1', createdAt: '2026-09-01',
    updatedBy: 'user-1', updatedAt: '2026-09-01', promotedBy: 'user-1', promotedAt: '2026-09-01', promotionHistory: [],
  })

  it('sustituye una dirección desconocida por las fuentes del primer universo', async () => {
    window.history.replaceState(null, '', '/pagina-inexistente')
    render(<App api={testApi()} session={session} />)

    expect(await screen.findByText('Crónica de Lumbrevela')).toBeInTheDocument()
    expect(window.location.pathname).toBe('/universos/realm-1/fuentes')
    expect(screen.getByRole('link', { name: 'Fuentes' })).toHaveAttribute('aria-current', 'page')
  })

  it('abre una fuente desde su dirección y vuelve con el historial del navegador', async () => {
    window.history.replaceState(null, '', '/universos/realm-1/fuentes/source-1')
    render(<App api={testApi()} session={session} />)

    expect(await screen.findByRole('article', { name: 'Fuente: Crónica de Lumbrevela' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Leer Crónica de Lumbrevela' })).toHaveAttribute('aria-current', 'page')
    expect(document.title).toBe('Fuentes · El Meridiano · Codex of Realms')

    fireEvent.click(screen.getByRole('link', { name: 'Consultas' }))
    expect(window.location.pathname).toBe('/universos/realm-1/consultas')
    expect(screen.queryByRole('article')).not.toBeInTheDocument()

    act(() => {
      window.history.replaceState(null, '', '/universos/realm-1/fuentes/source-1')
      window.dispatchEvent(new PopStateEvent('popstate'))
    })
    expect(await screen.findByRole('article', { name: 'Fuente: Crónica de Lumbrevela' })).toBeInTheDocument()
  })

  it('explica que una fuente enlazada ya no está disponible', async () => {
    window.history.replaceState(null, '', '/universos/realm-1/fuentes/retirada')
    const api = testApi()
    render(<App api={api} session={session} />)

    expect(await screen.findByRole('heading', { name: 'Esta fuente no está disponible' })).toBeInTheDocument()
    expect(api.getSourceContent).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('link', { name: 'Volver a las fuentes' }))
    expect(window.location.pathname).toBe('/universos/realm-1/fuentes')
  })

  it('abre una ficha del atlas enlazada y avisa si no es visible', async () => {
    const api = testApi()
    vi.mocked(api.listLoreEntities).mockResolvedValue([entity('aguja', 'La Aguja'), entity('lumbrevela', 'Lumbrevela')])
    window.history.replaceState(null, '', '/universos/realm-1/atlas/lumbrevela')
    render(<App api={api} session={session} />)

    const heading = await screen.findByRole('heading', { name: 'Lumbrevela', level: 2 })
    await waitFor(() => expect(heading).toHaveFocus())
    expect(screen.getByRole('link', { name: 'Abrir ficha de Lumbrevela' })).toHaveAttribute('aria-current', 'page')

    act(() => {
      window.history.pushState(null, '', '/universos/realm-1/atlas/secreta')
      window.dispatchEvent(new PopStateEvent('popstate'))
    })
    expect(await screen.findByText('Esta ficha no existe o no es visible para ti.')).toBeInTheDocument()
  })

  it('no deja a un jugador en personas y permisos', async () => {
    const api = testApi()
    vi.mocked(api.getCurrentUser).mockResolvedValue({
      user: { id: 'player-1', issuer: 'issuer', subject: 'player', emailVerified: true, displayName: 'Nara', email: null },
      realms: [{ id: 'realm-1', name: 'El Meridiano', role: 'PLAYER' }],
    })
    window.history.replaceState(null, '', '/universos/realm-1/personas')
    render(<App api={api} session={session} />)

    expect(await screen.findByText('Crónica de Lumbrevela')).toBeInTheDocument()
    expect(window.location.pathname).toBe('/universos/realm-1/fuentes')
    expect(api.listMemberships).not.toHaveBeenCalled()
  })

  it('pliega el índice en móvil al abrir una ficha y lo recupera al volver a la lista', async () => {
    const api = testApi()
    vi.mocked(api.listLoreEntities).mockResolvedValue([entity('aguja', 'La Aguja')])
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({ matches: true }))
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView
    onTestFinished(() => {
      vi.unstubAllGlobals()
      Reflect.deleteProperty(Element.prototype, 'scrollIntoView')
    })
    window.history.replaceState(null, '', '/universos/realm-1/atlas')
    render(<App api={api} session={session} />)
    // The owner keeps the new realm button in sight.
    expect((await screen.findByRole('button', { name: 'Nuevo universo' })).closest('details')).toBeNull()
    expect(scrollIntoView).not.toHaveBeenCalled()

    fireEvent.click(await screen.findByRole('link', { name: 'Abrir ficha de La Aguja' }))
    // On a phone the opened record moves to the top, past the realm header and the folded index.
    expect(scrollIntoView.mock.contexts).toContain(document.getElementById('workspace-content'))
    expect(screen.getByRole('button', { name: 'Abrir índice' })).toHaveAttribute('aria-expanded', 'false')
    fireEvent.click(screen.getByRole('button', { name: 'Abrir índice' }))
    expect(screen.getByRole('button', { name: 'Cerrar índice' })).toHaveAttribute('aria-expanded', 'true')
    fireEvent.click(screen.getByRole('link', { name: 'Fichas' }))
    expect(window.location.pathname).toBe('/universos/realm-1/atlas')
    expect(screen.getByRole('button', { name: 'Cerrar índice' })).toBeInTheDocument()
  })
})

describe('App: visibilidad', () => {
  async function withSources(api: ReturnType<typeof testApi>) {
    const [base] = await api.listSources('realm-1')
    if (!base) throw new Error('Falta la fuente de prueba')
    vi.mocked(api.listSources).mockResolvedValue([
      base,
      { ...base, id: 'source-2', title: 'El recuerdo de Nara', accessPolicyId: 'spoiler-1', visibility: 'SPOILER' },
      { ...base, id: 'source-3', title: 'La deuda de la Aguja', accessPolicyId: 'gm-1', visibility: 'GM_ONLY' },
    ])
  }

  it('avisa a una jugadora de lo que se le ha revelado y le ahorra los detalles técnicos', async () => {
    const api = testApi()
    vi.mocked(api.getCurrentUser).mockResolvedValue({
      user: { id: 'player-1', issuer: 'issuer', subject: 'tala', emailVerified: true, displayName: 'Tala', email: null },
      realms: [{ id: 'realm-1', name: 'El Meridiano', role: 'PLAYER' }],
    })
    await withSources(api)
    window.history.replaceState(null, '', '/universos/realm-1/fuentes/source-2')
    render(<App api={api} session={session} />)

    expect(await screen.findByRole('note')).toHaveTextContent('Revelado para tiLa dirección te ha mostrado este documento.')
    const entry = screen.getByRole('link', { name: 'Leer El recuerdo de Nara' }).closest('article')!
    expect(within(entry).getByText('Revelado para ti')).toHaveClass('visibility-spoiler')
    expect(screen.queryByText('lumbrevela.md · v2')).not.toBeInTheDocument()
    expect(screen.queryByText(/fragmentos/)).not.toBeInTheDocument()
  })

  it('muestra a la dirección a quién se ha revelado cada spoiler y cómo gestionarlo', async () => {
    const api = testApi()
    vi.mocked(api.listPolicies).mockResolvedValue([
      { id: 'policy-1', realmId: 'realm-1', classification: 'PUBLIC', name: 'Público', description: null },
      { id: 'spoiler-1', realmId: 'realm-1', classification: 'SPOILER', name: 'Recuerdos de Nara', description: null },
      { id: 'gm-1', realmId: 'realm-1', classification: 'GM_ONLY', name: 'Dirección', description: null },
    ])
    const tala = { userId: 'player-1', displayName: 'Tala', email: null, role: 'PLAYER' as const }
    vi.mocked(api.listMemberships).mockResolvedValue([
      { userId: 'user-1', displayName: 'Maestra del Meridiano', email: null, role: 'OWNER' }, tala,
    ])
    vi.mocked(api.listPolicyGrants).mockImplementation(async (_realmId, policyId) => policyId === 'spoiler-1' ? [tala] : [])
    await withSources(api)
    window.history.replaceState(null, '', '/universos/realm-1/fuentes/source-2')
    render(<App api={api} session={session} />)

    expect(await screen.findByText('Spoiler · Tala')).toHaveClass('visibility-spoiler')
    expect(screen.getByText('Solo dirección')).toHaveClass('visibility-gm')
    const notice = await screen.findByRole('note')
    expect(notice).toHaveTextContent('Spoiler · Recuerdos de Nara')
    expect(notice).toHaveTextContent('Revelado a Tala.')
    expect(screen.getByText('lumbrevela.md · v2 · 4 fragmentos')).toBeInTheDocument()

    fireEvent.click(within(notice).getByRole('link', { name: 'Gestionar quién lo ve' }))
    expect(window.location.pathname).toBe('/universos/realm-1/personas')
  })
})
