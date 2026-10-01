import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { visibilityLabel, type Visibility } from '../lib/visibility'
import { VisibilityMark } from './VisibilityMark'

describe('visibilidad', () => {
  it.each([
    ['PUBLIC', 'player', [], 'Público'],
    ['GM_ONLY', 'editor', [], 'Solo dirección'],
    ['SPOILER', 'player', [], 'Revelado para ti'],
    ['SPOILER', 'editor', [], 'Spoiler · sin revelar'],
    ['SPOILER', 'editor', ['Tala'], 'Spoiler · Tala'],
    ['SPOILER', 'editor', ['Tala', 'Nara', 'Iro', 'Maela'], 'Spoiler · Tala, Nara y 2 más'],
  ] as const)('nombra %s para %s', (visibility, viewer, revealedTo, label) => {
    expect(visibilityLabel(visibility, viewer, revealedTo)).toBe(label)
  })

  it('distingue cada visibilidad por forma y mantiene el nombre accesible en el modo compacto', () => {
    const { rerender } = render(<VisibilityMark visibility="GM_ONLY" viewer="editor" />)
    expect(screen.getByText('Solo dirección')).toHaveClass('visibility-mark', 'visibility-gm')

    rerender(<VisibilityMark compact visibility="SPOILER" viewer="editor" revealedTo={['Tala']} />)
    expect(screen.getByRole('img', { name: 'Spoiler · Tala' })).toBeInTheDocument()
    expect(screen.getByTitle('Spoiler · Tala')).toHaveClass('visibility-spoiler', 'is-compact')
  })

  it('no inventa una marca si el servidor no envía la visibilidad', () => {
    const { container } = render(<VisibilityMark visibility={'OTRA' as Visibility} viewer="player" />)
    expect(container).toBeEmptyDOMElement()
  })
})
