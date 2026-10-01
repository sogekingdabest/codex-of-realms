import { act, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { QuestionPanel } from './QuestionPanel'

function panel(asking: boolean) {
  return <QuestionPanel
    answer={null}
    asking={asking}
    loadingCitation={false}
    loadingRealm={false}
    onCancel={vi.fn()}
    onInspectCitation={vi.fn()}
    onSubmit={vi.fn()}
  />
}

describe('QuestionPanel', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('explica una espera larga y reinicia el contador en la siguiente consulta', () => {
    vi.useFakeTimers()
    const { rerender } = render(panel(true))
    expect(screen.getByRole('status')).toHaveTextContent('Contrastando fuentes y permisos…')
    expect(screen.getByText('0 s')).toBeInTheDocument()

    act(() => { vi.advanceTimersByTime(8_000) })

    expect(screen.getByRole('status')).toHaveTextContent('El modelo puede estar cargándose')
    expect(screen.getByText('8 s')).toBeInTheDocument()

    rerender(panel(false))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.getByText('Solo responderé con evidencia que puedas ver.')).toBeInTheDocument()

    rerender(panel(true))
    expect(screen.getByText('0 s')).toBeInTheDocument()
  })
})
