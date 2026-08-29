import { useEffect, useRef, useState, type RefObject, type SubmitEvent } from 'react'

import type { CodexApi } from './api'
import type { AuthSession } from './auth'
import { CatalogueWorkspace } from './CatalogueWorkspace'
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
  SourceEvidence,
} from './types'

interface AppProps {
  readonly api: CodexApi
  readonly session: AuthSession
}

interface EvidenceReference {
  documentId: string
  versionId: string
  heading: string | null
  startOffset: number
  endOffset: number
  eyebrow: string
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
  const [workspaceSection, setWorkspaceSection] = useState<'archive' | 'canon'>('archive')
  const [answer, setAnswer] = useState<LoreAnswer | null>(null)
  const [openEvidence, setOpenEvidence] = useState<{
    reference: EvidenceReference
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

  async function createRealm(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const name = formText(form, 'realmName')
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

  async function createPolicy(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedRealmId) return
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const name = formText(form, 'policyName')
    const description = formText(form, 'policyDescription')
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

  async function inviteMember(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedRealmId) return
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const email = formText(form, 'memberEmail')
    const role = form.get('memberRole') === 'EDITOR' ? 'EDITOR' : 'PLAYER'
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

  async function uploadSource(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedRealmId || !selectedPolicyId || !fileInput.current?.files?.[0]) return

    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const title = formText(form, 'title')
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

  async function askQuestion(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedRealmId) return
    const form = new FormData(event.currentTarget)
    const question = formText(form, 'question')
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

  async function inspectEvidence(reference: EvidenceReference) {
    if (!selectedRealmId) return
    setLoadingCitation(true)
    setRealmError(null)
    try {
      const source = await api.getSourceContent(
        selectedRealmId,
        reference.documentId,
        reference.versionId,
      )
      setOpenEvidence({ reference, source })
    } catch (error) {
      setRealmError(errorMessage(error))
    } finally {
      setLoadingCitation(false)
    }
  }

  function inspectCitation(citation: Citation) {
    return inspectEvidence({
      documentId: citation.sourceDocumentId,
      versionId: citation.documentVersionId,
      heading: citation.heading,
      startOffset: citation.startOffset,
      endOffset: citation.endOffset,
      eyebrow: `Evidencia autorizada · versión ${citation.versionNumber}`,
    })
  }

  function inspectCatalogueEvidence(evidence: SourceEvidence) {
    return inspectEvidence({
      documentId: evidence.documentId,
      versionId: evidence.documentVersionId,
      heading: evidence.heading,
      startOffset: evidence.startOffset,
      endOffset: evidence.endOffset,
      eyebrow: `Procedencia del canon · ${evidence.checksumSha256.slice(0, 10)}`,
    })
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
                  setOpenEvidence(null)
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

        {workspaceSection === 'archive' && capabilities && !runtimeReady && (
          <RuntimeNotice capabilities={capabilities} />
        )}

        {me.realms.length === 0 ? (
          <EmptyRealmPanel creating={creatingRealm} onSubmit={createRealm} />
        ) : (
          <>
            <nav className="workspace-navigation" aria-label="Espacio de trabajo">
              <button
                className={workspaceSection === 'archive' ? 'active' : ''}
                type="button"
                onClick={() => setWorkspaceSection('archive')}
              >
                Archivo y consultas
              </button>
              <button
                className={workspaceSection === 'canon' ? 'active' : ''}
                type="button"
                onClick={() => setWorkspaceSection('canon')}
              >
                Atlas del canon
              </button>
            </nav>

            {workspaceSection === 'archive' ? (
              <div className="content-grid" aria-busy={loadingRealm}>
            <SourcesPanel
              canEdit={Boolean(canEdit)}
              fileInput={fileInput}
              loading={loadingRealm}
              policies={policies}
              selectedPolicyId={selectedPolicyId}
              sources={sources}
              uploading={uploading}
              onDelete={deleteSource}
              onPolicyChange={setSelectedPolicyId}
              onUpload={uploadSource}
            />

            <QuestionPanel
              answer={answer}
              asking={asking}
              loadingCitation={loadingCitation}
              loadingRealm={loadingRealm}
              onInspectCitation={inspectCitation}
              onSubmit={askQuestion}
            />

            {canEdit && (
              <AccessPanel
                creatingPolicy={creatingPolicy}
                grantsByPolicy={grantsByPolicy}
                invitations={invitations}
                inviting={inviting}
                isOwner={Boolean(isOwner)}
                members={members}
                playerMembers={playerMembers}
                spoilerPolicies={spoilerPolicies}
                updating={updatingAccess}
                onCreatePolicy={createPolicy}
                onInvite={inviteMember}
                onRemoveMember={removeMember}
                onRevokeInvitation={revokeInvitation}
                onToggleGrant={toggleGrant}
              />
            )}
              </div>
            ) : (
              <CatalogueWorkspace
                api={api}
                canEdit={Boolean(canEdit)}
                key={selectedRealmId}
                policies={policies}
                realmId={selectedRealmId}
                sources={sources}
                onOpenEvidence={(evidence) => void inspectCatalogueEvidence(evidence)}
              />
            )}
          </>
        )}

        {openEvidence && (
          <EvidenceDialog evidence={openEvidence} onClose={() => setOpenEvidence(null)} />
        )}

        {realmError && (
          <ErrorToast message={realmError} onClose={() => setRealmError(null)} />
        )}
      </main>
    </div>
  )
}

function RuntimeNotice({ capabilities }: Readonly<{ capabilities: RuntimeCapabilities }>) {
  const modelToPull = capabilities.embedding.available
    ? capabilities.chat.model
    : capabilities.embedding.model
  return (
    <output className="runtime-notice">
      <div>
        <strong>El runtime local necesita preparación</strong>
        <span>
          {!capabilities.embedding.available && (
            <> Embeddings: {capabilities.embedding.model} ({capabilities.embedding.status}).</>
          )}
          {!capabilities.chat.available && (
            <> Respuestas: {capabilities.chat.model} ({capabilities.chat.status}).</>
          )}
        </span>
      </div>
      <code>ollama pull {modelToPull}</code>
    </output>
  )
}

function EmptyRealmPanel({ creating, onSubmit }: Readonly<{
  creating: boolean
  onSubmit: (event: SubmitEvent<HTMLFormElement>) => Promise<void>
}>) {
  return (
    <section className="empty-realm panel">
      <span className="panel-number">01</span>
      <div>
        <p className="eyebrow">Primer registro</p>
        <h2>Crea un universo</h2>
        <p>Será tu espacio aislado para fuentes, permisos y respuestas.</p>
        <form onSubmit={(event) => void onSubmit(event)}>
          <label>
            <span>Nombre del universo</span>
            <input name="realmName" maxLength={120} required placeholder="El Meridiano" />
          </label>
          <button disabled={creating} type="submit">
            {creating ? 'Creando…' : 'Crear universo'}
          </button>
        </form>
      </div>
    </section>
  )
}

function SourcesPanel({ canEdit, fileInput, loading, policies, selectedPolicyId, sources, uploading, onDelete, onPolicyChange, onUpload }: Readonly<{
  canEdit: boolean
  fileInput: RefObject<HTMLInputElement | null>
  loading: boolean
  policies: AccessPolicyView[]
  selectedPolicyId: string
  sources: SourceDocumentView[]
  uploading: boolean
  onDelete: (source: SourceDocumentView) => Promise<void>
  onPolicyChange: (policyId: string) => void
  onUpload: (event: SubmitEvent<HTMLFormElement>) => Promise<void>
}>) {
  return (
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
            <form className="upload-form" onSubmit={(event) => void onUpload(event)}>
              <label>
                <span>Título</span>
                <input name="title" maxLength={160} required placeholder="Crónica de Lumbrevela" />
              </label>
              <label>
                <span>Visibilidad</span>
                <select value={selectedPolicyId} onChange={(event) => onPolicyChange(event.target.value)} required>
                  {policies.map((policy) => (
                    <option key={policy.id} value={policy.id}>
                      {policy.name} · {classificationLabels[policy.classification]}
                    </option>
                  ))}
                </select>
              </label>
              <label className="file-field">
                <span>Archivo Markdown o TXT</span>
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
      <SourceList canEdit={canEdit} loading={loading} sources={sources} onDelete={onDelete} />
    </section>
  )
}

function QuestionPanel({ answer, asking, loadingCitation, loadingRealm, onInspectCitation, onSubmit }: Readonly<{
  answer: LoreAnswer | null
  asking: boolean
  loadingCitation: boolean
  loadingRealm: boolean
  onInspectCitation: (citation: Citation) => Promise<void>
  onSubmit: (event: SubmitEvent<HTMLFormElement>) => Promise<void>
}>) {
  return (
    <section className="panel question-panel">
      <div className="panel-heading">
        <span className="panel-number">02</span>
        <div>
          <p className="eyebrow">Consulta fundamentada</p>
          <h2>Pregunta al archivo</h2>
        </div>
      </div>
      <form className="question-form" onSubmit={(event) => void onSubmit(event)}>
        <label htmlFor="question">¿Qué quieres saber?</label>
        <textarea id="question" name="question" maxLength={1000} required placeholder="¿Por qué la Aguja conserva una deuda antigua?" rows={5} />
        <div className="question-footer">
          <small>Solo responderé con evidencia que puedas ver.</small>
          <button disabled={asking || loadingRealm} type="submit">
            {asking ? 'Buscando evidencia…' : 'Consultar'}
          </button>
        </div>
      </form>
      <div className="answer-region" aria-live="polite">
        {asking && <div className="thinking"><span aria-hidden="true" /><span>Contrastando fuentes y permisos…</span></div>}
        {answer?.outcome === 'INSUFFICIENT_EVIDENCE' && (
          <article className="insufficient">
            <p className="eyebrow">{failureEyebrow(answer.failureReason)}</p>
            <h3>{failureTitle(answer.failureReason)}</h3>
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
                      <p>{citation.heading || 'Documento'} · v{citation.versionNumber} · caracteres {citation.startOffset}–{citation.endOffset}</p>
                      <button className="citation-link" disabled={loadingCitation} type="button" onClick={() => void onInspectCitation(citation)}>
                        Abrir evidencia exacta
                      </button>
                    </div>
                  </li>
                ))}
              </ol>
            </div>
            <footer>{answer.provenance.chatProvider}/{answer.provenance.chatModel} · {answer.provenance.embeddingProvider}/{answer.provenance.embeddingModel}</footer>
          </article>
        )}
      </div>
    </section>
  )
}

function AccessPanel(props: Readonly<{
  creatingPolicy: boolean
  grantsByPolicy: Record<string, string[]>
  invitations: InvitationView[]
  inviting: boolean
  isOwner: boolean
  members: MembershipView[]
  playerMembers: MembershipView[]
  spoilerPolicies: AccessPolicyView[]
  updating: boolean
  onCreatePolicy: (event: SubmitEvent<HTMLFormElement>) => Promise<void>
  onInvite: (event: SubmitEvent<HTMLFormElement>) => Promise<void>
  onRemoveMember: (member: MembershipView) => Promise<void>
  onRevokeInvitation: (invitation: InvitationView) => Promise<void>
  onToggleGrant: (policyId: string, member: MembershipView) => Promise<void>
}>) {
  return (
    <section className="panel access-panel">
      <div className="panel-heading">
        <span className="panel-number">03</span>
        <div><p className="eyebrow">Colaboración sin spoilers</p><h2>Miembros y revelaciones</h2></div>
        <span className="count">{props.members.length}</span>
      </div>
      <div className="access-columns">
        <MembersSection {...props} />
        <SpoilerPoliciesSection {...props} />
      </div>
    </section>
  )
}

function MembersSection({ invitations, inviting, isOwner, members, updating, onInvite, onRemoveMember, onRevokeInvitation }: Readonly<{
  invitations: InvitationView[]
  inviting: boolean
  isOwner: boolean
  members: MembershipView[]
  updating: boolean
  onInvite: (event: SubmitEvent<HTMLFormElement>) => Promise<void>
  onRemoveMember: (member: MembershipView) => Promise<void>
  onRevokeInvitation: (invitation: InvitationView) => Promise<void>
}>) {
  const pendingInvitations = invitations.filter((invitation) => invitation.status === 'PENDING')
  return (
    <div className="access-section">
      <h3>Miembros</h3>
      {isOwner && (
        <form className="invite-form" onSubmit={(event) => void onInvite(event)}>
          <label><span>Correo de Keycloak</span><input name="memberEmail" type="email" maxLength={320} required placeholder="jugador@ejemplo.local" /></label>
          <label><span>Rol</span><select name="memberRole" defaultValue="PLAYER"><option value="PLAYER">Jugador</option><option value="EDITOR">Editor</option></select></label>
          <button disabled={inviting} type="submit">{inviting ? 'Invitando…' : 'Invitar'}</button>
        </form>
      )}
      <div className="member-list">
        {members.map((member) => (
          <article key={member.userId}>
            <div><strong>{member.displayName}</strong><span>{member.email || 'Sin correo'} · {roleLabels[member.role]}</span></div>
            {isOwner && member.role !== 'OWNER' && (
              <button className="text-danger" disabled={updating} type="button" onClick={() => void onRemoveMember(member)}>Quitar</button>
            )}
          </article>
        ))}
      </div>
      {isOwner && pendingInvitations.length > 0 && (
        <div className="pending-list">
          <h4>Invitaciones pendientes</h4>
          {pendingInvitations.map((invitation) => (
            <article key={invitation.id}>
              <span>{invitation.email} · {roleLabels[invitation.role]}</span>
              <button className="text-danger" disabled={updating} type="button" onClick={() => void onRevokeInvitation(invitation)}>Revocar</button>
            </article>
          ))}
        </div>
      )}
    </div>
  )
}

function SpoilerPoliciesSection({ creatingPolicy, grantsByPolicy, playerMembers, spoilerPolicies, updating, onCreatePolicy, onToggleGrant }: Readonly<{
  creatingPolicy: boolean
  grantsByPolicy: Record<string, string[]>
  playerMembers: MembershipView[]
  spoilerPolicies: AccessPolicyView[]
  updating: boolean
  onCreatePolicy: (event: SubmitEvent<HTMLFormElement>) => Promise<void>
  onToggleGrant: (policyId: string, member: MembershipView) => Promise<void>
}>) {
  return (
    <div className="access-section">
      <h3>Grupos de spoiler</h3>
      <form className="policy-form" onSubmit={(event) => void onCreatePolicy(event)}>
        <label><span>Nombre</span><input name="policyName" maxLength={120} required placeholder="Secreto de la Aguja" /></label>
        <label><span>Descripción opcional</span><input name="policyDescription" maxLength={300} placeholder="Revelado tras el capítulo 4" /></label>
        <button disabled={creatingPolicy} type="submit">{creatingPolicy ? 'Creando…' : 'Crear grupo'}</button>
      </form>
      {spoilerPolicies.length === 0 ? (
        <p className="muted">Crea un grupo cuando una fuente deba revelarse solo a ciertos jugadores.</p>
      ) : (
        <div className="spoiler-list">
          {spoilerPolicies.map((policy) => (
            <article key={policy.id}>
              <div><strong>{policy.name}</strong>{policy.description && <span>{policy.description}</span>}</div>
              <PolicyGrantList
                grants={grantsByPolicy[policy.id] ?? []}
                members={playerMembers}
                policyId={policy.id}
                updating={updating}
                onToggle={onToggleGrant}
              />
            </article>
          ))}
        </div>
      )}
    </div>
  )
}

function PolicyGrantList({ grants, members, policyId, updating, onToggle }: Readonly<{
  grants: string[]
  members: MembershipView[]
  policyId: string
  updating: boolean
  onToggle: (policyId: string, member: MembershipView) => Promise<void>
}>) {
  if (members.length === 0) return <small>Invita jugadores para conceder este conocimiento.</small>
  return (
    <div className="grant-list">
      {members.map((member) => (
        <label key={member.userId}>
          <input type="checkbox" disabled={updating} checked={grants.includes(member.userId)} onChange={() => void onToggle(policyId, member)} />
          <span>{member.displayName}</span>
        </label>
      ))}
    </div>
  )
}

function EvidenceDialog({ evidence, onClose }: Readonly<{
  evidence: { reference: EvidenceReference; source: SourceContentView }
  onClose: () => void
}>) {
  return (
    <dialog aria-labelledby="source-dialog-title" className="source-dialog-backdrop" open onClick={(event) => {
      if (event.target === event.currentTarget) onClose()
    }}>
      <div className="source-dialog">
        <header>
          <div>
            <p className="eyebrow">{evidence.reference.eyebrow}</p>
            <h2 id="source-dialog-title">{evidence.source.title}</h2>
            <span>{evidence.source.originalFilename} · {evidence.reference.heading || 'Documento'}</span>
          </div>
          <button type="button" aria-label="Cerrar evidencia" onClick={onClose}>×</button>
        </header>
        <pre>{citationExcerpt(evidence.source.content, evidence.reference)}</pre>
      </div>
    </dialog>
  )
}

function ErrorToast({ message, onClose }: Readonly<{ message: string; onClose: () => void }>) {
  return (
    <div className="error-toast" role="alert">
      <strong>No se pudo completar la operación.</strong>
      <span>{message}</span>
      <button type="button" aria-label="Cerrar aviso" onClick={onClose}>×</button>
    </div>
  )
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Ha ocurrido un error inesperado.'
}

function formText(form: FormData, field: string) {
  const value = form.get(field)
  return typeof value === 'string' ? value.trim() : ''
}

function failureEyebrow(reason: LoreAnswer['failureReason']) {
  return reason === 'MODEL_UNAVAILABLE' ? 'Runtime no disponible' : 'Resultado seguro'
}

function failureTitle(reason: LoreAnswer['failureReason']) {
  return reason === 'MODEL_UNAVAILABLE'
    ? 'No se pudo consultar el modelo'
    : 'No hay una respuesta verificable'
}

function sourceStatusLabel(status: SourceDocumentView['status']) {
  if (status === 'READY') return 'Lista'
  if (status === 'FAILED') return 'Fallida'
  return 'Procesando'
}

function SourceList({ canEdit, loading, sources, onDelete }: Readonly<{
  canEdit: boolean
  loading: boolean
  sources: SourceDocumentView[]
  onDelete: (source: SourceDocumentView) => Promise<void>
}>) {
  if (loading) {
    return <div className="source-list" aria-live="polite"><p className="muted">Leyendo el catálogo…</p></div>
  }

  if (sources.length === 0) {
    return (
      <div className="source-list" aria-live="polite">
        <div className="empty-state">
          <span aria-hidden="true">◇</span>
          <p>Todavía no hay fuentes visibles en este universo.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="source-list" aria-live="polite">
      {sources.map((source) => (
        <article className="source-item" key={source.id}>
          <div>
            <h3>{source.title}</h3>
            <p>{source.originalFilename} · versión {source.versionNumber}</p>
          </div>
          <div className="source-meta">
            <span className={`status status-${source.status.toLowerCase()}`}>
              {sourceStatusLabel(source.status)}
            </span>
            <small>{source.chunkCount} fragmentos</small>
            {canEdit && (
              <button
                className="text-danger"
                type="button"
                onClick={() => void onDelete(source)}
              >
                Eliminar
              </button>
            )}
          </div>
        </article>
      ))}
    </div>
  )
}

function citationExcerpt(content: string, reference: Pick<EvidenceReference, 'startOffset' | 'endOffset'>) {
  const start = Math.max(0, Math.min(reference.startOffset, content.length))
  const end = Math.max(start, Math.min(reference.endOffset, content.length))
  const contextStart = Math.max(0, start - 320)
  const contextEnd = Math.min(content.length, end + 320)
  const prefix = contextStart > 0 ? '…' : ''
  const suffix = contextEnd < content.length ? '…' : ''
  return `${prefix}${content.slice(contextStart, start)}⟦ ${content.slice(start, end)} ⟧${content.slice(end, contextEnd)}${suffix}`
}
