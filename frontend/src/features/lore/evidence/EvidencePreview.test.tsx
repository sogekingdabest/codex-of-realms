import { act, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { SourceContentView, SourceEvidence } from '../../content'
import { EvidencePreview } from './EvidencePreview'

const evidence: SourceEvidence = { documentId: 'source-1', documentVersionId: 'v2', chunkId: 'chunk-1', sourceTitle: 'Crónica', checksumSha256: 'checksum', heading: 'La Aguja', startOffset: 7, endOffset: 19 }
const source: SourceContentView = { documentId: 'source-1', versionId: 'v2', title: 'Crónica', originalFilename: 'cronica.md', content: 'Antes. Una deuda.  Después.' }

describe('EvidencePreview', () => {
  it('muestra solo el pasaje literal de la versión enlazada', async () => {
    const contentApi = { getSourceContent: vi.fn().mockResolvedValue(source) }
    render(<EvidencePreview contentApi={contentApi} realmId="realm-1" evidence={evidence} />)
    expect(await screen.findByRole('blockquote')).toHaveTextContent('Una deuda.')
    expect(screen.queryByText(/Después/)).not.toBeInTheDocument()
    expect(contentApi.getSourceContent).toHaveBeenCalledWith('realm-1', 'source-1', 'v2', expect.any(AbortSignal))
  })

  it.each([
    { startOffset: -1, endOffset: 19 },
    { startOffset: 7, endOffset: 7 },
    { startOffset: 7, endOffset: 100 },
  ])('no inventa un fragmento si los límites son inválidos: %j', async (offsets) => {
    const contentApi = { getSourceContent: vi.fn().mockResolvedValue(source) }
    render(<EvidencePreview contentApi={contentApi} realmId="realm-1" evidence={{ ...evidence, ...offsets }} />)
    expect(await screen.findByText(/No se pudo mostrar/)).toBeVisible()
    expect(screen.queryByRole('blockquote')).not.toBeInTheDocument()
  })

  it('deja disponible la lectura manual si la vista previa falla', async () => {
    const contentApi = { getSourceContent: vi.fn().mockRejectedValue(new Error('No disponible')) }
    render(<EvidencePreview contentApi={contentApi} realmId="realm-1" evidence={evidence} />)
    expect(await screen.findByText(/Abre la fuente/)).toBeVisible()
  })

  it('aborta la lectura anterior e ignora una respuesta tardía al cambiar de ficha', async () => {
    let finish!: (value: SourceContentView) => void
    const contentApi = { getSourceContent: vi.fn().mockImplementationOnce(() => new Promise<SourceContentView>((resolve) => { finish = resolve })).mockResolvedValue(source) }
    const { rerender } = render(<EvidencePreview key="first" contentApi={contentApi} realmId="realm-1" evidence={evidence} />)
    const signal = contentApi.getSourceContent.mock.calls[0][3] as AbortSignal
    rerender(<EvidencePreview key="second" contentApi={contentApi} realmId="realm-2" evidence={evidence} />)
    expect(signal.aborted).toBe(true)
    await screen.findByRole('blockquote')
    await act(async () => finish({ ...source, content: 'Otra versión que llegó tarde' }))
    expect(screen.getByRole('blockquote')).toHaveTextContent('Una deuda.')
  })
})
