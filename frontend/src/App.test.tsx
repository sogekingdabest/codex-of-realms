import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import type { CodexApi } from './api'
import { App } from './App'
import type { AuthSession } from './auth'

function testApi(): CodexApi {
  return {
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
      { id: 'policy-1', realmId: 'realm-1', classification: 'PUBLIC' },
    ]),
    createPolicy: vi.fn(),
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
    }),
  }
}

const session: AuthSession = {
  displayName: 'Maestra',
  getAccessToken: vi.fn().mockResolvedValue('token'),
  logout: vi.fn().mockResolvedValue(undefined),
}

describe('App', () => {
  it('carga el realm y presenta sus fuentes visibles', async () => {
    render(<App api={testApi()} session={session} />)

    expect(await screen.findByText('El Meridiano')).toBeInTheDocument()
    expect(await screen.findByText('Crónica de Lumbrevela')).toBeInTheDocument()
    expect(screen.getByText('lumbrevela.md · versión 2')).toBeInTheDocument()
    expect(screen.getByText('4 fragmentos')).toBeInTheDocument()
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
  })
})
