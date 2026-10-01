import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import type { LoreAnswer } from './model'
import { QuestionPanel } from './QuestionPanel'

const provenance = { embeddingProvider: 'ollama', embeddingModel: 'bge-m3', chatProvider: 'ollama', chatModel: 'qwen3.5:4b' }

const answered: LoreAnswer = {
  answerMode: 'EXTRACTIVE',
  outcome: 'ANSWERED',
  answer: null,
  excerpts: [{ text: 'Nara no entregó las monedas.', citationRank: 1 }],
  citations: [{
    rank: 1, realmId: 'realm', chunkId: 'chunk', sourceDocumentId: 'source', documentVersionId: 'version',
    versionNumber: 1, sourceTitle: 'Crónica', heading: null, startOffset: 0, endOffset: 28,
  }],
  provenance,
  failureReason: null,
}

const unanswered: LoreAnswer = {
  answerMode: 'EXTRACTIVE',
  outcome: 'INSUFFICIENT_EVIDENCE',
  answer: null,
  excerpts: [],
  citations: [],
  provenance,
  failureReason: 'NO_EVIDENCE',
}

function panel(asking: boolean, answer: LoreAnswer | null = null) {
  return <QuestionPanel
    answer={answer}
    asking={asking}
    loadingCitation={false}
    loadingRealm={false}
    onCancel={vi.fn()}
    onInspectCitation={vi.fn()}
    onSubmit={vi.fn()}
  />
}

/** Places every element at the given viewport position; jsdom has no layout. */
function layoutAt(top: number) {
  vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, top, 300, 20))
  const scrollIntoView = vi.fn()
  Element.prototype.scrollIntoView = scrollIntoView
  return scrollIntoView
}

function ask(rerender: ReturnType<typeof render>['rerender'], answer: LoreAnswer) {
  fireEvent.submit(screen.getByLabelText('¿Qué quieres saber?'))
  rerender(panel(true))
  rerender(panel(false, answer))
}

describe('QuestionPanel', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
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

  it('lleva el foco al resultado y lo desplaza a la vista cuando llega por debajo de la pantalla', () => {
    const scrollIntoView = layoutAt(window.innerHeight + 15)
    const { rerender } = render(panel(false))

    ask(rerender, answered)

    expect(screen.getByRole('heading', { name: 'Fragmentos de las fuentes' })).toHaveFocus()
    expect(scrollIntoView).toHaveBeenCalledExactlyOnceWith({ block: 'start' })
    expect(scrollIntoView.mock.contexts[0]).toBe(screen.getByRole('article'))
  })

  it('enfoca un resultado sin respuesta sin mover la página si ya se ve', () => {
    const scrollIntoView = layoutAt(400)
    const { rerender } = render(panel(false))

    ask(rerender, unanswered)

    expect(screen.getByRole('heading', { name: 'No hay una respuesta verificable' })).toHaveFocus()
    expect(scrollIntoView).not.toHaveBeenCalled()
  })

  it('no quita el cursor a quien ya escribe la siguiente pregunta', () => {
    const scrollIntoView = layoutAt(window.innerHeight + 15)
    const { rerender } = render(panel(false))
    const question = screen.getByLabelText('¿Qué quieres saber?')
    fireEvent.change(question, { target: { value: '¿Quién guarda las monedas?' } })
    question.focus()
    fireEvent.submit(question)
    rerender(panel(true))

    // iOS leaves focus on the submitted question: that alone must not keep the result hidden.
    rerender(panel(false, unanswered))
    expect(screen.getByRole('heading', { name: 'No hay una respuesta verificable' })).toHaveFocus()

    question.focus()
    fireEvent.submit(question)
    rerender(panel(true))
    fireEvent.change(question, { target: { value: '¿Y dónde están ahora?' } })
    rerender(panel(false, answered))

    expect(question).toHaveFocus()
    expect(scrollIntoView).toHaveBeenCalledOnce()
  })

  it('no mueve el foco al volver a una consulta que ya tenía resultado', () => {
    layoutAt(window.innerHeight + 15)
    render(panel(false, answered))

    expect(screen.getByRole('heading', { name: 'Fragmentos de las fuentes' })).not.toHaveFocus()
  })
})
