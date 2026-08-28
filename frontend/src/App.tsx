import { useEffect, useRef, useState, type FormEvent } from 'react'

import type { CodexApi } from './api'
import type { AuthSession } from './auth'
import type {
  AccessClassification,
  AccessPolicyView,
  Citation,
  CurrentUserView,
  InvitationView,
  LoreAnswer,
  MembershipView,
  RealmSummary,
  RuntimeCapabilities,
  SourceContentView,
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

const failureMessages: Record<NonNullable<LoreAnswer['failureReason']>, string> = {
  NO_EVIDENCE: 'El archivo no contiene información visible que permita responder con garantías.',
  LOW_RELEVANCE: 'Hay contenido relacionado, pero no es suficientemente preciso para sostener una respuesta.',
  UNSAFE_INPUT: 'La consulta o la evidencia contiene instrucciones inseguras y se ha rechazado.',
  MODEL_UNAVAILABLE: 'El modelo de respuesta no está disponible. Revisa el estado del runtime local.',
  VALIDATION_FAILED: 'El modelo respondió, pero la respuesta no superó la validación de evidencia y citas.',
}

export function App({ api, session }: AppProps) {
  const [me, setMe] = useState<CurrentUserView | null>(null)
  const [selectedRealmId, setSelectedRealmId] = useState('')
  const [sources, setSources] = useState<SourceDocumentView[]>([])
  const [policies, setPolicies] = useState<AccessPolicyView[]>([])
  const [members, setMembers] = useState<MembershipView[]>([])
  const [invitations, setInvitations] = useState<InvitationView[]>([])
  const [grantsByPolicy, setGrantsByPolicy] = useState<Record<string, string[]>>({})
  const [capabilities, setCapabilities] = useState<RuntimeCapabilities | null>(null)
  const [selectedPolicyId, setSelectedPolicyId] = useState('')
  const [answer, setAnswer] = useState<LoreAnswer | null>(null)
  const [openCitation, setOpenCitation] = useState<{
    citation: Citation
    source: SourceContentView
  } | null>(null)
  const [initialError, setInitialError] = useState<string | null>(null)
  const [realmError, setRealmError] = useState<string | null>(null)
  const [loadedRealmId, setLoadedRealmId] = useState('')
  const [creatingRealm, setCreatingRealm] = useState(false)
  const [creatingPolicy, setCreatingPolicy] = useState(false)
  const [inviting, setInviting] = useState(false)
  const [updatingAccess, setUpdatingAccess] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [asking, setAsking] = useState(false)
  const [loadingCitation, setLoadingCitation] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  const selectedRealm = me?.realms.find((realm) => realm.id === selectedRealmId)
  const canEdit = selectedRealm?.role === 'OWNER' || selectedRealm?.role === 'EDITOR'
  const isOwner = selectedRealm?.role === 'OWNER'
  const loadingRealm = Boolean(selectedRealmId) && loadedRealmId !== selectedRealmId
  const runtimeReady = capabilities?.chat.available && capabilities.embedding.available
  const spoilerPolicies = policies.filter((policy) => policy.classification === 'SPOILER')
  const playerMembers = members.filter((member) => member.role === 'PLAYER')

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
    let active = true
    api.getCapabilities()
      .then((value) => {
        if (active) setCapabilities(value)
      })
      .catch(() => {
        if (active) setCapabilities(null)
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

    const loadRealm = async () => {
      const [nextSources, nextPolicies, nextMembers, nextInvitations] = await Promise.all([
        api.listSources(selectedRealmId),
        canEdit ? api.listPolicies(selectedRealmId) : Promise.resolve<AccessPolicyView[]>([]),
        canEdit ? api.listMemberships(selectedRealmId) : Promise.resolve<MembershipView[]>([]),
        isOwner ? api.listInvitations(selectedRealmId) : Promise.resolve<InvitationView[]>([]),
      ])
      const grantEntries = await Promise.all(
        nextPolicies
          .filter((policy) => policy.classification === 'SPOILER')
          .map(async (policy) => [
            policy.id,
            (await api.listPolicyGrants(selectedRealmId, policy.id)).map((member) => member.userId),
          ] as const),
      )
      return { nextSources, nextPolicies, nextMembers, nextInvitations, grantEntries }
    }

    loadRealm()
      .then(({ nextSources, nextPolicies, nextMembers, nextInvitations, grantEntries }) => {
        if (!active) return
        setSources(nextSources)
        setPolicies(nextPolicies)
        setMembers(nextMembers)
        setInvitations(nextInvitations)
        setGrantsByPolicy(Object.fromEntries(grantEntries))
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
  }, [api, canEdit, isOwner, selectedRealm, selectedRealmId])

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

  async function createPolicy(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedRealmId) return
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const name = String(form.get('policyName') ?? '').trim()
    const description = String(form.get('policyDescription') ?? '').trim()
    if (!name) return
    setCreatingPolicy(true)
    setRealmError(null)
    try {
      const policy = await api.createPolicy(
        selectedRealmId,
        'SPOILER',
        name,
        description || undefined,
      )
      setPolicies((current) => [...current, policy])
      setGrantsByPolicy((current) => ({ ...current, [policy.id]: [] }))
      setSelectedPolicyId(policy.id)
      formElement.reset()
    } catch (error) {
      setRealmError(errorMessage(error))
    } finally {
      setCreatingPolicy(false)
    }
  }

  async function inviteMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedRealmId) return
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const email = String(form.get('memberEmail') ?? '').trim()
    const role = String(form.get('memberRole') ?? 'PLAYER') as 'EDITOR' | 'PLAYER'
    if (!email) return
    setInviting(true)
    setRealmError(null)
    try {
      const invitation = await api.inviteMember(selectedRealmId, email, role)
      setInvitations((current) => [
        invitation,
        ...current.filter((item) => item.id !== invitation.id),
      ])
      setMembers(await api.listMemberships(selectedRealmId))
      formElement.reset()
    } catch (error) {
      setRealmError(errorMessage(error))
    } finally {
      setInviting(false)
    }
  }

  async function removeMember(member: MembershipView) {
    if (!selectedRealmId || member.role === 'OWNER') return
    if (!window.confirm(`¿Quitar a ${member.displayName} de este universo?`)) return
    setUpdatingAccess(true)
    setRealmError(null)
    try {
      await api.removeMembership(selectedRealmId, member.userId)
      setMembers((current) => current.filter((item) => item.userId !== member.userId))
      setGrantsByPolicy((current) => Object.fromEntries(
        Object.entries(current).map(([policyId, userIds]) => [
          policyId,
          userIds.filter((userId) => userId !== member.userId),
        ]),
      ))
    } catch (error) {
      setRealmError(errorMessage(error))
    } finally {
      setUpdatingAccess(false)
    }
  }

  async function revokeInvitation(invitation: InvitationView) {
    if (!selectedRealmId) return
    setUpdatingAccess(true)
    setRealmError(null)
    try {
      await api.revokeInvitation(selectedRealmId, invitation.id)
      setInvitations((current) => current.map((item) =>
        item.id === invitation.id ? { ...item, status: 'REVOKED' } : item,
      ))
    } catch (error) {
      setRealmError(errorMessage(error))
    } finally {
      setUpdatingAccess(false)
    }
  }

  async function toggleGrant(policyId: string, member: MembershipView) {
    if (!selectedRealmId) return
    const granted = grantsByPolicy[policyId]?.includes(member.userId) ?? false
    setUpdatingAccess(true)
    setRealmError(null)
    try {
      if (granted) {
        await api.revokePolicy(selectedRealmId, policyId, member.userId)
      } else {
        await api.grantPolicy(selectedRealmId, policyId, member.userId)
      }
      setGrantsByPolicy((current) => ({
        ...current,
        [policyId]: granted
          ? (current[policyId] ?? []).filter((userId) => userId !== member.userId)
          : [...(current[policyId] ?? []), member.userId],
      }))
    } catch (error) {
      setRealmError(errorMessage(error))
    } finally {
      setUpdatingAccess(false)
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

  async function deleteSource(source: SourceDocumentView) {
    if (!selectedRealmId) return
    if (!window.confirm(`¿Eliminar «${source.title}» y retirarla de las respuestas?`)) return
    setRealmError(null)
    try {
      await api.deleteSource(selectedRealmId, source.id)
      setSources((current) => current.filter((item) => item.id !== source.id))
    } catch (error) {
      setRealmError(errorMessage(error))
    }
  }

  async function inspectCitation(citation: Citation) {
    if (!selectedRealmId) return
    setLoadingCitation(true)
    setRealmError(null)
    try {
      const source = await api.getSourceContent(
        selectedRealmId,
        citation.sourceDocumentId,
        citation.documentVersionId,
      )
      setOpenCitation({ citation, source })
    } catch (error) {
      setRealmError(errorMessage(error))
    } finally {
      setLoadingCitation(false)
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
                  setSources([])
                  setPolicies([])
                  setMembers([])
                  setInvitations([])
                  setGrantsByPolicy({})
                  setOpenCitation(null)
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

        {capabilities && !runtimeReady && (
          <aside className="runtime-notice" role="status">
            <div>
              <strong>El runtime local necesita preparación</strong>
              <span>
                {!capabilities.embedding.available
                  ? ` Embeddings: ${capabilities.embedding.model} (${capabilities.embedding.status}).`
                  : ''}
                {!capabilities.chat.available
                  ? ` Respuestas: ${capabilities.chat.model} (${capabilities.chat.status}).`
                  : ''}
              </span>
            </div>
            <code>
              ollama pull {!capabilities.embedding.available
                ? capabilities.embedding.model
                : capabilities.chat.model}
            </code>
          </aside>
        )}

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
                  {policies.length > 0 ? (
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
                              {policy.name} · {classificationLabels[policy.classification]}
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
                  ) : (
                    <p className="muted">Preparando las políticas base del universo…</p>
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
                        {canEdit && (
                          <button
                            className="text-danger"
                            type="button"
                            onClick={() => void deleteSource(source)}
                          >
                            Eliminar
                          </button>
                        )}
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
                    <p className="eyebrow">
                      {answer.failureReason === 'MODEL_UNAVAILABLE' ? 'Runtime no disponible' : 'Resultado seguro'}
                    </p>
                    <h3>
                      {answer.failureReason === 'MODEL_UNAVAILABLE'
                        ? 'No se pudo consultar el modelo'
                        : 'No hay una respuesta verificable'}
                    </h3>
                    <p>{failureMessages[answer.failureReason ?? 'NO_EVIDENCE']}</p>
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
                              <button
                                className="citation-link"
                                disabled={loadingCitation}
                                type="button"
                                onClick={() => void inspectCitation(citation)}
                              >
                                Abrir evidencia exacta
                              </button>
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

            {canEdit && (
              <section className="panel access-panel">
                <div className="panel-heading">
                  <span className="panel-number">03</span>
                  <div>
                    <p className="eyebrow">Colaboración sin spoilers</p>
                    <h2>Miembros y revelaciones</h2>
                  </div>
                  <span className="count">{members.length}</span>
                </div>

                <div className="access-columns">
                  <div className="access-section">
                    <h3>Miembros</h3>
                    {isOwner && (
                      <form className="invite-form" onSubmit={(event) => void inviteMember(event)}>
                        <label>
                          Correo de Keycloak
                          <input name="memberEmail" type="email" maxLength={320} required placeholder="jugador@ejemplo.local" />
                        </label>
                        <label>
                          Rol
                          <select name="memberRole" defaultValue="PLAYER">
                            <option value="PLAYER">Jugador</option>
                            <option value="EDITOR">Editor</option>
                          </select>
                        </label>
                        <button disabled={inviting} type="submit">
                          {inviting ? 'Invitando…' : 'Invitar'}
                        </button>
                      </form>
                    )}
                    <div className="member-list">
                      {members.map((member) => (
                        <article key={member.userId}>
                          <div>
                            <strong>{member.displayName}</strong>
                            <span>{member.email || 'Sin correo'} · {roleLabels[member.role]}</span>
                          </div>
                          {isOwner && member.role !== 'OWNER' && (
                            <button
                              className="text-danger"
                              disabled={updatingAccess}
                              type="button"
                              onClick={() => void removeMember(member)}
                            >
                              Quitar
                            </button>
                          )}
                        </article>
                      ))}
                    </div>
                    {isOwner && invitations.some((invitation) => invitation.status === 'PENDING') && (
                      <div className="pending-list">
                        <h4>Invitaciones pendientes</h4>
                        {invitations.filter((invitation) => invitation.status === 'PENDING').map((invitation) => (
                          <article key={invitation.id}>
                            <span>{invitation.email} · {roleLabels[invitation.role]}</span>
                            <button
                              className="text-danger"
                              disabled={updatingAccess}
                              type="button"
                              onClick={() => void revokeInvitation(invitation)}
                            >
                              Revocar
                            </button>
                          </article>
                        ))}
                      </div>
                    )}
                  </div>

                  <div className="access-section">
                    <h3>Grupos de spoiler</h3>
                    <form className="policy-form" onSubmit={(event) => void createPolicy(event)}>
                      <label>
                        Nombre
                        <input name="policyName" maxLength={120} required placeholder="Secreto de la Aguja" />
                      </label>
                      <label>
                        Descripción opcional
                        <input name="policyDescription" maxLength={300} placeholder="Revelado tras el capítulo 4" />
                      </label>
                      <button disabled={creatingPolicy} type="submit">
                        {creatingPolicy ? 'Creando…' : 'Crear grupo'}
                      </button>
                    </form>

                    {spoilerPolicies.length === 0 ? (
                      <p className="muted">Crea un grupo cuando una fuente deba revelarse solo a ciertos jugadores.</p>
                    ) : (
                      <div className="spoiler-list">
                        {spoilerPolicies.map((policy) => (
                          <article key={policy.id}>
                            <div>
                              <strong>{policy.name}</strong>
                              {policy.description && <span>{policy.description}</span>}
                            </div>
                            {playerMembers.length === 0 ? (
                              <small>Invita jugadores para conceder este conocimiento.</small>
                            ) : (
                              <div className="grant-list">
                                {playerMembers.map((member) => (
                                  <label key={member.userId}>
                                    <input
                                      type="checkbox"
                                      disabled={updatingAccess}
                                      checked={grantsByPolicy[policy.id]?.includes(member.userId) ?? false}
                                      onChange={() => void toggleGrant(policy.id, member)}
                                    />
                                    {member.displayName}
                                  </label>
                                ))}
                              </div>
                            )}
                          </article>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </section>
            )}
          </div>
        )}

        {openCitation && (
          <div className="source-dialog-backdrop" role="presentation" onMouseDown={() => setOpenCitation(null)}>
            <section
              aria-labelledby="source-dialog-title"
              aria-modal="true"
              className="source-dialog"
              role="dialog"
              onMouseDown={(event) => event.stopPropagation()}
            >
              <header>
                <div>
                  <p className="eyebrow">Evidencia autorizada · versión {openCitation.citation.versionNumber}</p>
                  <h2 id="source-dialog-title">{openCitation.source.title}</h2>
                  <span>{openCitation.source.originalFilename} · {openCitation.citation.heading || 'Documento'}</span>
                </div>
                <button type="button" aria-label="Cerrar evidencia" onClick={() => setOpenCitation(null)}>×</button>
              </header>
              <pre>{citationExcerpt(openCitation.source.content, openCitation.citation)}</pre>
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

function citationExcerpt(content: string, citation: Citation) {
  const start = Math.max(0, Math.min(citation.startOffset, content.length))
  const end = Math.max(start, Math.min(citation.endOffset, content.length))
  const contextStart = Math.max(0, start - 320)
  const contextEnd = Math.min(content.length, end + 320)
  const prefix = contextStart > 0 ? '…' : ''
  const suffix = contextEnd < content.length ? '…' : ''
  return `${prefix}${content.slice(contextStart, start)}⟦ ${content.slice(start, end)} ⟧${content.slice(end, contextEnd)}${suffix}`
}
