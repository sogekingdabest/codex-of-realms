import { render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { SourceText } from './SourceText'

afterEach(() => {
  vi.restoreAllMocks()
})

const campaign = [
  '## Rutas',
  '',
  'Consulta el [mapa](https://example.test/mapa) o el [anexo](anexo.md).',
  '',
  '3. Salir al alba',
  '   1. Revisar balizas',
  '4. Cruzar',
  '',
  '- [x] Pagar la deuda',
  '',
  '> Nadie cruza',
  '> de noche.',
  '',
  '| Lugar | Riesgo |',
  '| --- | --- |',
  '| Aramonte | Alto |',
  '',
  '```',
  'ceniza = true',
  '```',
  '',
  '---',
].join('\n')

describe('SourceText', () => {
  it('muestra la estructura del Markdown con enlaces externos seguros', () => {
    render(<SourceText content={campaign} headingLevel={3} />)

    expect(screen.getByRole('heading', { name: 'Rutas', level: 5 })).toBeInTheDocument()
    const external = screen.getByRole('link', { name: 'mapa' })
    expect(external).toHaveAttribute('href', 'https://example.test/mapa')
    expect(external).toHaveAttribute('rel', 'noopener noreferrer')
    expect(screen.queryByRole('link', { name: 'anexo' })).not.toBeInTheDocument()
    expect(external.closest('p')).toHaveTextContent('Consulta el mapa o el anexo.')

    const [steps, nested, tasks] = screen.getAllByRole('list')
    expect(steps.tagName).toBe('OL')
    expect(steps).toHaveAttribute('start', '3')
    expect(within(nested).getByText('Revisar balizas')).toBeInTheDocument()
    expect(within(tasks).getByRole('checkbox')).toBeChecked()

    expect(screen.getByText(/Nadie cruza/).closest('blockquote')).not.toBeNull()
    expect(screen.getByRole('cell', { name: 'Aramonte' })).toBeInTheDocument()
    expect(screen.getByText('ceniza = true').closest('pre')).not.toBeNull()
    expect(document.querySelector('hr')).not.toBeNull()
  })

  it('marca una cita que atraviesa formato y la lleva a la vista', () => {
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView
    const content = 'Antes. La **Aguja** guarda la luz. Después.'
    const start = content.indexOf('La ')
    const end = content.indexOf(' Después')

    render(<SourceText content={content} passage={{ start, end }} />)

    const marks = Array.from(document.querySelectorAll('mark'), (mark) => mark.textContent)
    expect(marks).toEqual(['La ', 'Aguja', ' guarda la luz.'])
    expect(document.querySelector('p')).toHaveClass('is-cited')
    expect(scrollIntoView).toHaveBeenCalledWith({ block: 'center' })
  })

  it('recorta el pasaje sin mostrar metadatos ni texto ajeno', () => {
    const content = '---\ntitle: x\n---\n## Lumbrevela\n\nSe alza al este.\n\nOtro tema.'
    const start = content.indexOf('## Lumbrevela')
    const end = content.indexOf('Otro')

    render(<SourceText content={content} passage={{ start, end }} display="passage" headingLevel={3} />)

    expect(screen.getByRole('heading', { name: 'Lumbrevela' })).toBeInTheDocument()
    expect(screen.getByText('Se alza al este.')).toBeInTheDocument()
    expect(screen.queryByText(/Otro tema/)).not.toBeInTheDocument()
    expect(screen.queryByText('Metadatos del documento')).not.toBeInTheDocument()
    expect(document.querySelector('mark')).toBeNull()
  })

  it('recorta listas, tablas y código al pasaje', () => {
    const content = '- uno\n- dos\n\n| a | b |\n| - | - |\n| c | d |\n\n```\nx\n```\n\n***'
    const start = content.indexOf('dos')
    const end = content.indexOf('| a')

    render(<SourceText content={content} passage={{ start, end }} display="passage" />)

    expect(screen.getByText('dos')).toBeInTheDocument()
    expect(screen.queryByText('uno')).not.toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.queryByText('x')).not.toBeInTheDocument()
    expect(document.querySelector('hr')).toBeNull()
  })

  it('no muestra nada en modo pasaje si la cita está fuera del texto', () => {
    const { container } = render(<SourceText content="Texto." passage={{ start: 20, end: 30 }} display="passage" />)
    expect(container.querySelector('.source-text')).toBeEmptyDOMElement()
  })
})
