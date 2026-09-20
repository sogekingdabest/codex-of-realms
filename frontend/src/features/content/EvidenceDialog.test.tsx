import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { EvidenceDialog } from './EvidenceDialog'

describe('EvidenceDialog', () => {
  it('muestra contexto y elipsis alrededor de una evidencia intermedia', () => {
    const content = `${'a'.repeat(400)}EVIDENCIA${'z'.repeat(400)}`
    render(
      <EvidenceDialog
        evidence={{
          reference: {
            documentId: 'document-1',
            versionId: 'version-1',
            heading: null,
            startOffset: 400,
            endOffset: 409,
            eyebrow: 'Evidencia autorizada',
          },
          source: {
            documentId: 'document-1',
            versionId: 'version-1',
            title: 'Crónica',
            originalFilename: 'cronica.md',
            content,
            excludedSentences: 1,
          },
        }}
        onClose={vi.fn()}
      />,
    )

    expect(screen.getByText(/…a+⟦ EVIDENCIA ⟧z+…/)).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('1 frase(s) de más de 2.000 caracteres')
    expect(screen.getByText('cronica.md · Documento')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Leer documento completo' }))
    expect(screen.getByLabelText('Contenido de la fuente')).toHaveTextContent(content)
    fireEvent.click(screen.getByRole('button', { name: 'Volver al fragmento citado' }))
    expect(screen.getByText(/…a+⟦ EVIDENCIA ⟧z+…/)).toBeInTheDocument()
  })
})
