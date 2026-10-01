import { useEffect, useRef, useState, type SubmitEvent } from 'react'

import { SourceText } from '../../shared/ui/SourceText'
import type { Citation, LoreAnswer } from './model'

const failureMessages: Record<NonNullable<LoreAnswer['failureReason']>, string> = {
  NO_EVIDENCE: 'El archivo no contiene información visible que permita responder con garantías.',
  LOW_RELEVANCE: 'Hay contenido relacionado, pero no es suficientemente preciso para sostener una respuesta.',
  UNSAFE_INPUT: 'La consulta o la evidencia contiene instrucciones inseguras y se ha rechazado.',
  MODEL_UNAVAILABLE: 'El modelo no respondió a tiempo o no está disponible. Si acaba de arrancar, puede seguir cargándose: vuelve a intentarlo en un minuto.',
  VALIDATION_FAILED: 'El modelo respondió, pero la respuesta no superó la validación de evidencia y citas.',
}

/** After this many seconds the wait is explained: the model may be loading. */
const SLOW_QUESTION_SECONDS = 8

export function QuestionPanel({ answer, asking, loadingCitation, loadingRealm, onCancel, onInspectCitation, onSubmit }: Readonly<{
  answer: LoreAnswer | null
  asking: boolean
  loadingCitation: boolean
  loadingRealm: boolean
  onCancel: () => void
  onInspectCitation: (citation: Citation) => Promise<void>
  onSubmit: (event: SubmitEvent<HTMLFormElement>) => Promise<void>
}>) {
  const question = useRef<HTMLTextAreaElement>(null)
  const elapsed = useElapsedSeconds(asking)
  function cancel() {
    onCancel()
    question.current?.focus()
  }
  return (
    <section className="panel question-panel">
      <div className="panel-heading"><div><p className="eyebrow">Consulta fundamentada</p><h2>Pregunta al archivo</h2></div></div>
      <form className="question-form" onSubmit={(event) => void onSubmit(event)}>
        <label htmlFor="question">¿Qué quieres saber?</label>
        <textarea ref={question} id="question" name="question" maxLength={1000} required placeholder="¿Por qué la Aguja conserva una deuda antigua?" rows={5} />
        <p className="muted">Las consultas usan las fuentes publicadas. Las fichas del atlas se mantienen por separado.</p>
        <div className="question-footer">
          {asking
            ? <p className="question-progress" role="status">
              <span className="progress-mark" aria-hidden="true" />
              <span>{elapsed < SLOW_QUESTION_SECONDS
                ? 'Contrastando fuentes y permisos…'
                : 'El modelo puede estar cargándose. Tras un rato sin uso, la primera consulta tarda uno o dos minutos.'}</span>
              <span className="question-elapsed" aria-hidden="true">{elapsed} s</span>
            </p>
            : <small>Solo responderé con evidencia que puedas ver.</small>}
          <div className="question-actions">
            {asking && <button type="button" className="quiet-button" onClick={cancel}>Cancelar</button>}
            <button disabled={asking || loadingRealm} type="submit">{asking ? 'Buscando evidencia…' : 'Consultar'}</button>
          </div>
        </div>
      </form>
      <div className="answer-region" aria-live="polite">
        {answer?.outcome === 'INSUFFICIENT_EVIDENCE' && (
          <article className="insufficient"><p className="eyebrow">{failureEyebrow(answer.failureReason)}</p><h3>{failureTitle(answer.failureReason)}</h3><p>{failureMessages[answer.failureReason ?? 'NO_EVIDENCE']}</p></article>
        )}
        {answer?.outcome === 'ANSWERED' && (
          <article className="answer-card">
            <p className="eyebrow">Fragmentos de las fuentes</p>
            <p className="muted">Pasajes literales seleccionados para tu consulta. Las fuentes pueden ser incompletas o discrepar.</p>
            {answer.excerpts.map((excerpt) => {
              const citation = answer.citations.find((item) => item.rank === excerpt.citationRank)
              return <div key={excerpt.citationRank} className="answer-copy">
                <blockquote className="answer-excerpt"><SourceText content={excerpt.text} headingLevel={3} /></blockquote>
                {citation && <button className="citation-link" disabled={loadingCitation} type="button" onClick={() => void onInspectCitation(citation)}>Abrir contexto [{citation.rank}]</button>}
              </div>
            })}
            <div className="citations"><h3>Fuentes citadas</h3><ol>{answer.citations.map((citation) => (
              <li key={citation.rank}><span>{citation.rank}</span><div><strong>{citation.sourceTitle}</strong><p>{citation.heading || 'Documento'} · v{citation.versionNumber} · caracteres {citation.startOffset}–{citation.endOffset}</p><button className="citation-link" disabled={loadingCitation} type="button" onClick={() => void onInspectCitation(citation)}>Abrir evidencia exacta</button></div></li>
            ))}</ol></div>
            <footer>{answer.provenance.chatProvider}/{answer.provenance.chatModel} · {answer.provenance.embeddingProvider}/{answer.provenance.embeddingModel}</footer>
          </article>
        )}
      </div>
    </section>
  )
}

function useElapsedSeconds(running: boolean) {
  const [elapsed, setElapsed] = useState(0)
  const [wasRunning, setWasRunning] = useState(running)
  if (running !== wasRunning) {
    setWasRunning(running)
    setElapsed(0)
  }
  useEffect(() => {
    if (!running) return
    const started = Date.now()
    const timer = window.setInterval(() => setElapsed(Math.floor((Date.now() - started) / 1000)), 1000)
    return () => window.clearInterval(timer)
  }, [running])
  return elapsed
}

function failureEyebrow(reason: LoreAnswer['failureReason']) { return reason === 'MODEL_UNAVAILABLE' ? 'Modelo no disponible' : 'Resultado seguro' }
function failureTitle(reason: LoreAnswer['failureReason']) { return reason === 'MODEL_UNAVAILABLE' ? 'No se pudo consultar el modelo' : 'No hay una respuesta verificable' }
