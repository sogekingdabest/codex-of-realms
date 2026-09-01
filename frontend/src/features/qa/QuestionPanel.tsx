import type { SubmitEvent } from 'react'

import type { Citation, LoreAnswer } from './model'

const failureMessages: Record<NonNullable<LoreAnswer['failureReason']>, string> = {
  NO_EVIDENCE: 'El archivo no contiene información visible que permita responder con garantías.',
  LOW_RELEVANCE: 'Hay contenido relacionado, pero no es suficientemente preciso para sostener una respuesta.',
  UNSAFE_INPUT: 'La consulta o la evidencia contiene instrucciones inseguras y se ha rechazado.',
  MODEL_UNAVAILABLE: 'El modelo de respuesta no está disponible. Revisa el estado del runtime local.',
  VALIDATION_FAILED: 'El modelo respondió, pero la respuesta no superó la validación de evidencia y citas.',
}

export function QuestionPanel({ answer, asking, loadingCitation, loadingRealm, onInspectCitation, onSubmit }: Readonly<{
  answer: LoreAnswer | null
  asking: boolean
  loadingCitation: boolean
  loadingRealm: boolean
  onInspectCitation: (citation: Citation) => Promise<void>
  onSubmit: (event: SubmitEvent<HTMLFormElement>) => Promise<void>
}>) {
  return (
    <section className="panel question-panel">
      <div className="panel-heading"><span className="panel-number">02</span><div><p className="eyebrow">Consulta fundamentada</p><h2>Pregunta al archivo</h2></div></div>
      <form className="question-form" onSubmit={(event) => void onSubmit(event)}>
        <label htmlFor="question">¿Qué quieres saber?</label>
        <textarea id="question" name="question" maxLength={1000} required placeholder="¿Por qué la Aguja conserva una deuda antigua?" rows={5} />
        <div className="question-footer"><small>Solo responderé con evidencia que puedas ver.</small><button disabled={asking || loadingRealm} type="submit">{asking ? 'Buscando evidencia…' : 'Consultar'}</button></div>
      </form>
      <div className="answer-region" aria-live="polite">
        {asking && <div className="thinking"><span aria-hidden="true" /><span>Contrastando fuentes y permisos…</span></div>}
        {answer?.outcome === 'INSUFFICIENT_EVIDENCE' && (
          <article className="insufficient"><p className="eyebrow">{failureEyebrow(answer.failureReason)}</p><h3>{failureTitle(answer.failureReason)}</h3><p>{failureMessages[answer.failureReason ?? 'NO_EVIDENCE']}</p></article>
        )}
        {answer?.outcome === 'ANSWERED' && (
          <article className="answer-card">
            <p className="eyebrow">Respuesta verificada</p><div className="answer-copy">{answer.answer}</div>
            <div className="citations"><h3>Fuentes citadas</h3><ol>{answer.citations.map((citation) => (
              <li key={citation.chunkId}><span>{citation.rank}</span><div><strong>{citation.sourceTitle}</strong><p>{citation.heading || 'Documento'} · v{citation.versionNumber} · caracteres {citation.startOffset}–{citation.endOffset}</p><button className="citation-link" disabled={loadingCitation} type="button" onClick={() => void onInspectCitation(citation)}>Abrir evidencia exacta</button></div></li>
            ))}</ol></div>
            <footer>{answer.provenance.chatProvider}/{answer.provenance.chatModel} · {answer.provenance.embeddingProvider}/{answer.provenance.embeddingModel}</footer>
          </article>
        )}
      </div>
    </section>
  )
}

function failureEyebrow(reason: LoreAnswer['failureReason']) { return reason === 'MODEL_UNAVAILABLE' ? 'Runtime no disponible' : 'Resultado seguro' }
function failureTitle(reason: LoreAnswer['failureReason']) { return reason === 'MODEL_UNAVAILABLE' ? 'No se pudo consultar el modelo' : 'No hay una respuesta verificable' }
