import { useEffect, useRef, useState, type FormEvent } from 'react'

import type { CodexApi } from './api'
import type { AuthSession } from './auth'
import type {
  AccessClassification,
  AccessPolicyView,
  CurrentUserView,
  LoreAnswer,
  RealmSummary,
  SourceDocumentView,
} from './types'

interface AppProps {
  api: CodexApi
  session: AuthSession
}

const classificationLabels: Record<AccessClassification, string> = {
  PUBLIC: 'Pública',
  GM_ONLY: 'Solo dirección',
  SPOILER: 'Spoiler con permiso',
}

const roleLabels: Record<RealmSummary['role'], string> = {
  OWNER: 'Propietario',
  EDITOR: 'Editor',
  PLAYER: 'Jugador',
}

export function App({ api, session }: AppProps) {
  const [me, setMe] = useState<CurrentUserView | null>(null)
  const [selectedRealmId, setSelectedRealmId] = useState('')
  const [sources, setSources] = useState<SourceDocumentView[]>([])
  const [policies, setPolicies] = useState<AccessPolicyView[]>([])
  const [selectedPolicyId, setSelectedPolicyId] = useState('')
  const [answer, setAnswer] = useState<LoreAnswer | null>(null)
  const [initialError, setInitialError] = useState<string | null>(null)
  const [realmError, setRealmError] = useState<string | null>(null)
  const [loadedRealmId, setLoadedRealmId] = useState('')
  const [creatingRealm, setCreatingRealm] = useState(false)
  const [creatingPolicy, setCreatingPolicy] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [asking, setAsking] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  const selectedRealm = me?.realms.find((realm) => realm.id === selectedRealmId)
  const canEdit = selectedRealm?.role === 'OWNER' || selectedRealm?.role === 'EDITOR'
  const loadingRealm = Boolean(selectedRealmId) && loadedRealmId !== selectedRealmId

  useEffect(() => {
    let active = true
    api
      .getCurrentUser()
      .then((currentUser) => {
        if (!active) return
        setMe(currentUser)
        setSelectedRealmId(currentUser.realms[0]?.id ?? '')
      })
      .catch((error: unknown) => {
        if (active) setInitialError(errorMessage(error))
      })
    return () => {
      active = false
    }
  }, [api])

  useEffect(() => {
    if (!selectedRealmId || !selectedRealm) {
      return
    }

    let active = true

    const policiesRequest = canEdit
      ? api.listPolicies(selectedRealmId)
      : Promise.resolve<AccessPolicyView[]>([])

    Promise.all([api.listSources(selectedRealmId), policiesRequest])
      .then(([nextSources, nextPolicies]) => {
        if (!active) return
        setSources(nextSources)
        setPolicies(nextPolicies)
        setSelectedPolicyId(nextPolicies[0]?.id ?? '')
        setLoadedRealmId(selectedRealmId)
      })
      .catch((error: unknown) => {
        if (active) {
          setRealmError(errorMessage(error))
          setLoadedRealmId(selectedRealmId)
        }
      })

    return () => {
      active = false
    }
  }, [api, canEdit, selectedRealm, selectedRealmId])

  async function createRealm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const name = String(form.get('realmName') ?? '').trim()
    if (!name) return

    setCreatingRealm(true)
    setInitialError(null)
    try {
      const realm = await api.createRealm(name)
      setMe((current) =>
        current ? { ...current, realms: [...current.realms, realm] } : current,
      )
      setSelectedRealmId(realm.id)
      formElement.reset()
    } catch (error) {
      setInitialError(errorMessage(error))
    } finally {
      setCreatingRealm(false)
    }
  }

  async function createPolicy(classification: AccessClassification) {
    if (!selectedRealmId) return
    setCreatingPolicy(true)
    setRealmError(null)
    try {
      const policy = await api.createPolicy(selectedRealmId, classification)
      setPolicies((current) => [...current, policy])
      setSelectedPolicyId(policy.id)
    } catch (error) {
      setRealmError(errorMessage(error))
    } finally {
      setCreatingPolicy(false)
    }
  }

  async function uploadSource(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedRealmId || !selectedPolicyId || !fileInput.current?.files?.[0]) return

    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const title = String(form.get('title') ?? '').trim()
    const file = fileInput.current.files[0]
    if (!title) return

    setUploading(true)
    setRealmError(null)
    try {
      const source = await api.uploadSource(
        selectedRealmId,
        title,
        selectedPolicyId,
        file,
      )
      setSources((current) => [source, ...current.filter((item) => item.id !== source.id)])
      formElement.reset()
      setSelectedPolicyId(policies[0]?.id ?? '')
    } catch (error) {
      setRealmError(errorMessage(error))
    } finally {
      setUploading(false)
    }
  }

  async function askQuestion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedRealmId) return
    const form = new FormData(event.currentTarget)
    const question = String(form.get('question') ?? '').trim()
    if (!question) return

    setAsking(true)
    setRealmError(null)
    setAnswer(null)
    try {
      setAnswer(await api.ask(selectedRealmId, question))
    } catch (error) {
      setRealmError(errorMessage(error))
    } finally {
      setAsking(false)
    }
  }

  if (initialError) {
    return (
      <main className="startup-error">
        <p className="eyebrow">Sesión iniciada</p>
        <h1>No pudimos cargar tu archivo</h1>
        <p>{initialError}</p>
        <button type="button" onClick={() => window.location.reload()}>
          Reintentar
        </button>
      </main>
    )
  }

  if (!me) {
    return (
      <main className="loading-screen" aria-live="polite">
        <span className="sigil" aria-hidden="true"><span>C</span></span>
        <p>Abriendo el archivo…</p>
      </main>
    )
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="Codex of Realms, inicio">
          <span className="brand-mark" aria-hidden="true"><span>C</span></span>
          <span>
            <strong>Codex</strong>
            <small>of Realms</small>
          </span>
        </a>
        <div className="identity">
          <span>
            <small>Conectado como</small>
            <strong>{me.user.displayName || session.displayName}</strong>
          </span>
          <button className="quiet-button" type="button" onClick={() => void session.logout()}>
            Cerrar sesión
          </button>
        </div>
      </header>

      <main className="workspace">
        <section className="hero">
          <div>
            <p className="eyebrow">Archivo vivo · evidencia antes que elocuencia</p>
            <h1>Consulta tu mundo.<br /><em>Conserva su verdad.</em></h1>
          </div>
          {me.realms.length > 0 && (
            <label className="realm-picker">
              <span>Universo activo</span>
              <select
                value={selectedRealmId}
                onChange={(event) => {
                  setRealmError(null)
                  setAnswer(null)
                  setSelectedRealmId(event.target.value)
                }}
              >
                {me.realms.map((realm) => (
                  <option key={realm.id} value={realm.id}>{realm.name}</option>
                ))}
              </select>
              {selectedRealm && <small>{roleLabels[selectedRealm.role]}</small>}
            </label>
          )}
        </section>

        {me.realms.length === 0 ? (
          <section className="empty-realm panel">
            <span className="panel-number">01</span>
            <div>
              <p className="eyebrow">Primer registro</p>
              <h2>Crea un universo</h2>
              <p>Será tu espacio aislado para fuentes, permisos y respuestas.</p>
              <form onSubmit={(event) => void createRealm(event)}>
                <label>
                  Nombre del universo
                  <input name="realmName" maxLength={120} required placeholder="El Meridiano" />
                </label>
                <button disabled={creatingRealm} type="submit">
                  {creatingRealm ? 'Creando…' : 'Crear universo'}
                </button>
              </form>
            </div>
          </section>
        ) : (
          <div className="content-grid" aria-busy={loadingRealm}>
            <section className="panel sources-panel">
              <div className="panel-heading">
                <span className="panel-number">01</span>
                <div>
                  <p className="eyebrow">Biblioteca del realm</p>
                  <h2>Fuentes</h2>
                </div>
                <span className="count">{sources.length}</span>
              </div>

              {canEdit && (
                <div className="upload-card">
                  <h3>Añadir conocimiento</h3>
                  {policies.length === 0 ? (
                    <div className="policy-empty">
                      <p>Antes de subir, crea una política de visibilidad.</p>
                      <div className="policy-actions">
                        {(Object.keys(classificationLabels) as AccessClassification[]).map(
                          (classification) => (
                            <button
                              className="chip-button"
                              disabled={creatingPolicy}
                              key={classification}
                              onClick={() => void createPolicy(classification)}
                              type="button"
                            >
                              + {classificationLabels[classification]}
                            </button>
                          ),
                        )}
                      </div>
                    </div>
                  ) : (
                    <form className="upload-form" onSubmit={(event) => void uploadSource(event)}>
                      <label>
                        Título
                        <input name="title" maxLength={160} required placeholder="Crónica de Lumbrevela" />
                      </label>
                      <label>
                        Visibilidad
                        <select
                          value={selectedPolicyId}
                          onChange={(event) => setSelectedPolicyId(event.target.value)}
                          required
                        >
                          {policies.map((policy) => (
                            <option key={policy.id} value={policy.id}>
                              {classificationLabels[policy.classification]}
                            </option>
                          ))}
                        </select>
                      </label>
                      <label className="file-field">
                        Archivo Markdown o TXT
                        <input ref={fileInput} name="file" type="file" accept=".md,.txt,text/markdown,text/plain" required />
                      </label>
                      <button disabled={uploading} type="submit">
                        {uploading ? 'Procesando…' : 'Subir y procesar'}
                      </button>
                    </form>
                  )}
                </div>
              )}

              <div className="source-list" aria-live="polite">
                {loadingRealm ? (
                  <p className="muted">Leyendo el catálogo…</p>
                ) : sources.length === 0 ? (
                  <div className="empty-state">
                    <span aria-hidden="true">◇</span>
                    <p>Todavía no hay fuentes visibles en este universo.</p>
                  </div>
                ) : (
                  sources.map((source) => (
                    <article className="source-item" key={source.id}>
                      <div>
                        <h3>{source.title}</h3>
                        <p>{source.originalFilename} · versión {source.versionNumber}</p>
                      </div>
                      <div className="source-meta">
                        <span className={`status status-${source.status.toLowerCase()}`}>
                          {source.status === 'READY' ? 'Lista' : source.status === 'FAILED' ? 'Fallida' : 'Procesando'}
                        </span>
                        <small>{source.chunkCount} fragmentos</small>
                      </div>
                    </article>
                  ))
                )}
              </div>
            </section>

            <section className="panel question-panel">
              <div className="panel-heading">
                <span className="panel-number">02</span>
                <div>
                  <p className="eyebrow">Consulta fundamentada</p>
                  <h2>Pregunta al archivo</h2>
                </div>
              </div>

              <form className="question-form" onSubmit={(event) => void askQuestion(event)}>
                <label htmlFor="question">¿Qué quieres saber?</label>
                <textarea
                  id="question"
                  name="question"
                  maxLength={1000}
                  required
                  placeholder="¿Por qué la Aguja conserva una deuda antigua?"
                  rows={5}
                />
                <div className="question-footer">
                  <small>Solo responderé con evidencia que puedas ver.</small>
                  <button disabled={asking || loadingRealm} type="submit">
                    {asking ? 'Buscando evidencia…' : 'Consultar'}
                  </button>
                </div>
              </form>

              <div className="answer-region" aria-live="polite">
                {asking && (
                  <div className="thinking">
                    <span aria-hidden="true" />
                    Contrastando fuentes y permisos…
                  </div>
                )}
                {answer?.outcome === 'INSUFFICIENT_EVIDENCE' && (
                  <article className="insufficient">
                    <p className="eyebrow">Resultado seguro</p>
                    <h3>No hay evidencia suficiente</h3>
                    <p>El archivo no contiene información visible que permita responder con garantías.</p>
                  </article>
                )}
                {answer?.outcome === 'ANSWERED' && (
                  <article className="answer-card">
                    <p className="eyebrow">Respuesta verificada</p>
                    <div className="answer-copy">{answer.answer}</div>
                    <div className="citations">
                      <h3>Fuentes citadas</h3>
                      <ol>
                        {answer.citations.map((citation) => (
                          <li key={citation.chunkId}>
                            <span>{citation.rank}</span>
                            <div>
                              <strong>{citation.sourceTitle}</strong>
                              <p>
                                {citation.heading || 'Documento'} · v{citation.versionNumber} · caracteres {citation.startOffset}–{citation.endOffset}
                              </p>
                            </div>
                          </li>
                        ))}
                      </ol>
                    </div>
                    <footer>
                      {answer.provenance.chatProvider}/{answer.provenance.chatModel} · {answer.provenance.embeddingProvider}/{answer.provenance.embeddingModel}
                    </footer>
                  </article>
                )}
              </div>
            </section>
          </div>
        )}

        {realmError && (
          <div className="error-toast" role="alert">
            <strong>No se pudo completar la operación.</strong>
            <span>{realmError}</span>
            <button type="button" aria-label="Cerrar aviso" onClick={() => setRealmError(null)}>×</button>
          </div>
        )}
      </main>
    </div>
  )
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Ha ocurrido un error inesperado.'
}
