import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { EvidenceDialog, SourceReader } from './EvidenceDialog'

const content = [
  '---',
  'classification: PUBLIC',
  '---',
  '# Crónica',
  '',
  'Primer párrafo lejano.',
  '',
  'Párrafo anterior.',
  '',
  'La Aguja guarda **una deuda** antigua.',
  '',
  'Párrafo posterior.',
  '',
  'Último párrafo lejano.',
].join('\n')
const cited = 'una deuda'
const source = { documentId: 'document-1', versionId: 'version-1', title: 'Crónica', originalFilename: 'cronica.md', content }

function reference(startOffset: number, endOffset: number) {
  return { documentId: 'document-1', versionId: 'version-1', heading: null, startOffset, endOffset, eyebrow: 'Evidencia autorizada' }
}

describe('EvidenceDialog', () => {
  it('muestra el fragmento citado con su contexto y permite leer el documento completo', () => {
    const start = content.indexOf(cited)
    render(<EvidenceDialog evidence={{ reference: reference(start, start + cited.length), source: { ...source, excludedSentences: 1 } }} onClose={vi.fn()} />)

    const reader = screen.getByRole('region', { name: 'Contenido de la fuente' })
    expect(reader.querySelector('mark')).toHaveTextContent(cited)
    expect(reader.querySelector('mark')?.closest('strong')).not.toBeNull()
    expect(reader).toHaveTextContent('Párrafo anterior.')
    expect(reader).toHaveTextContent('Párrafo posterior.')
    expect(reader).not.toHaveTextContent('Primer párrafo lejano.')
    expect(reader).not.toHaveTextContent('**')
    expect(within(reader).getAllByText('Texto omitido')).toHaveLength(2)
    expect(screen.getByRole('status')).toHaveTextContent('1 frase(s) de más de 2.000 caracteres')
    expect(screen.getByText('cronica.md · Documento')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Leer documento completo' }))
    expect(within(reader).getByRole('heading', { name: 'Crónica' })).toBeInTheDocument()
    expect(reader).toHaveTextContent('Primer párrafo lejano.')
    expect(reader).not.toHaveTextContent('# Crónica')
    expect(reader.querySelector('mark')).toHaveTextContent(cited)
    expect(reader.querySelector('details')).not.toHaveAttribute('open')

    fireEvent.click(screen.getByRole('button', { name: 'Volver al fragmento citado' }))
    expect(reader).not.toHaveTextContent('Primer párrafo lejano.')
  })

  it('muestra el documento completo si la cita no se puede localizar', () => {
    render(<EvidenceDialog evidence={{ reference: reference(content.length + 10, content.length + 20), source }} onClose={vi.fn()} />)

    expect(screen.getByRole('status')).toHaveTextContent('No se pudo localizar el fragmento citado')
    expect(screen.queryByRole('button', { name: 'Leer documento completo' })).not.toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Contenido de la fuente' })).toHaveTextContent('Último párrafo lejano.')
  })
})

describe('SourceReader', () => {
  it('lee Markdown formateado y un TXT sin interpretarlo', () => {
    const { rerender } = render(<SourceReader evidence={{ source }} onClose={vi.fn()} />)
    const reader = screen.getByRole('region', { name: 'Contenido de la fuente' })
    expect(within(reader).getByRole('heading', { name: 'Crónica', level: 3 })).toBeInTheDocument()
    expect(within(reader).getByText('Metadatos del documento')).toBeInTheDocument()

    rerender(<SourceReader evidence={{ source: { ...source, originalFilename: 'notas.TXT', content: '# No es un título\n*literal*' } }} onClose={vi.fn()} />)
    expect(within(reader).queryByRole('heading')).not.toBeInTheDocument()
    expect(reader).toHaveTextContent('# No es un título *literal*')
  })
})
