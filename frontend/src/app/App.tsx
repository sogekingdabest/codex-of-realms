import { useEffect, useState, type SubmitEvent } from 'react'

import type { AuthSession } from '../shared/auth'
import { errorMessage, isAbortError } from '../shared/lib/errors'
import { formText } from '../shared/lib/forms'
import { EmptyRealmPanel, type CurrentUserView, type RealmSummary } from '../features/realm'
import { RuntimeNotice, type RuntimeCapabilities } from '../features/runtime'
import type { ApiClients } from './ApiClients'
import { RealmWorkspace, type WorkspaceSection } from './workspace/RealmWorkspace'

interface AppProps {
  readonly api: ApiClients
  readonly session: AuthSession
}

const roleLabels: Record<RealmSummary['role'], string> = {
  OWNER: 'Propietario', EDITOR: 'Editor', PLAYER: 'Jugador',
}

export function App({ api, session }: AppProps) {
  const [me, setMe] = useState<CurrentUserView | null>(null)
  const [selectedRealmId, setSelectedRealmId] = useState('')
  const [workspaceSection, setWorkspaceSection] = useState<WorkspaceSection>('sources')
  const [indexContainer, setIndexContainer] = useState<HTMLDivElement | null>(null)
  const [mobileIndexOpen, setMobileIndexOpen] = useState(false)
  const [capabilities, setCapabilities] = useState<RuntimeCapabilities | null>(null)
  const [initialError, setInitialError] = useState<string | null>(null)
  const [creatingRealm, setCreatingRealm] = useState(false)
  const [realmCreationError, setRealmCreationError] = useState<string | null>(null)
  const [showRealmForm, setShowRealmForm] = useState(false)
  const selectedRealm = me?.realms.find((realm) => realm.id === selectedRealmId)
  const runtimeReady = capabilities?.chat.available && capabilities.embedding.available

  useEffect(() => {
    const controller = new AbortController()
    api.realm.getCurrentUser(controller.signal)
      .then((currentUser) => {
        if (controller.signal.aborted) return
        setMe(currentUser)
        setSelectedRealmId(currentUser.realms[0]?.id ?? '')
      })
      .catch((reason: unknown) => { if (!isAbortError(reason)) setInitialError(errorMessage(reason)) })
    return () => controller.abort()
  }, [api.realm])

  useEffect(() => {
    const controller = new AbortController()
    api.runtime.getCapabilities(controller.signal)
      .then((value) => { if (!controller.signal.aborted) setCapabilities(value) })
      .catch(() => { if (!controller.signal.aborted) setCapabilities(null) })
    return () => controller.abort()
  }, [api.runtime])

  async function createRealm(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const formElement = event.currentTarget
    const name = formText(new FormData(formElement), 'realmName')
    if (!name || creatingRealm) return
    setCreatingRealm(true)
    setRealmCreationError(null)
    try {
      const realm = await api.realm.createRealm(name)
      setMe((current) => current ? { ...current, realms: [...current.realms, realm] } : current)
      setSelectedRealmId(realm.id)
      formElement.reset()
      setWorkspaceSection('sources')
      setShowRealmForm(false)
    } catch (reason) {
      setRealmCreationError(errorMessage(reason))
    } finally {
      setCreatingRealm(false)
    }
  }

  if (initialError) {
    return <main className="startup-error"><p className="eyebrow">Sesión iniciada</p><h1>No pudimos cargar tu archivo</h1><p>{initialError}</p><button type="button" onClick={() => window.location.reload()}>Reintentar</button></main>
  }
  if (!me) {
    return <main className="loading-screen" aria-live="polite"><span className="loading-wordmark">Codex of Realms</span><p>Abriendo el archivo…</p></main>
  }

  return (
    <div className="app-shell">
      <a className="skip-link" href="#workspace-content">Saltar al contenido</a>
      <header className="topbar">
        <a className="brand" href="/" aria-label="Codex of Realms, inicio">Codex of Realms</a>
        <div className="identity"><span>{me.user.displayName || session.displayName}</span><button className="quiet-button" type="button" onClick={() => void session.logout()}>Cerrar sesión</button></div>
      </header>
      <div className={`workspace${me.realms.length > 0 ? ' has-realm' : ''}`}>
        {selectedRealm && <aside className="archive-sidebar" aria-label="Índice del archivo">
          <div className="realm-controls">
            <p className="eyebrow">Archivo de campaña</p>
            <h1>{selectedRealm.name}</h1>
            <details className="realm-switcher"><summary>{roleLabels[selectedRealm.role]} · Cambiar universo</summary><label className="realm-picker"><span>Universo activo</span><select aria-label="Universo activo" value={selectedRealmId} onChange={(event) => { setSelectedRealmId(event.target.value); setShowRealmForm(false); if (workspaceSection === 'access') setWorkspaceSection('sources') }}>{me.realms.map((realm) => <option key={realm.id} value={realm.id}>{realm.name}</option>)}</select></label></details>
            <button type="button" className="quiet-button" aria-expanded={showRealmForm}
              aria-controls="new-realm" disabled={creatingRealm}
              onClick={() => { setShowRealmForm((current) => !current); setRealmCreationError(null) }}>Nuevo universo</button>
          </div>
          <nav className="workspace-navigation" aria-label="Espacio de trabajo">
            <button className={workspaceSection === 'canon' ? 'active' : ''} aria-pressed={workspaceSection === 'canon'} type="button" onClick={() => setWorkspaceSection('canon')}>Atlas del canon</button>
            <button className={workspaceSection === 'sources' ? 'active' : ''} aria-pressed={workspaceSection === 'sources'} type="button" onClick={() => setWorkspaceSection('sources')}>Fuentes</button>
            <button className={workspaceSection === 'questions' ? 'active' : ''} aria-pressed={workspaceSection === 'questions'} type="button" onClick={() => setWorkspaceSection('questions')}>Consultas</button>
            {selectedRealm.role !== 'PLAYER' && <button className={workspaceSection === 'access' ? 'active' : ''} aria-pressed={workspaceSection === 'access'} type="button" onClick={() => setWorkspaceSection('access')}>Personas y permisos</button>}
          </nav>
          {(workspaceSection === 'canon' || workspaceSection === 'sources') && <button className="mobile-index-toggle quiet-button" type="button" aria-expanded={mobileIndexOpen} aria-controls="archive-index" onClick={() => setMobileIndexOpen((open) => !open)}>{mobileIndexOpen ? 'Cerrar índice' : 'Abrir índice'}</button>}
          <div id="archive-index" className={`archive-index-slot${mobileIndexOpen ? ' is-open' : ''}`} ref={setIndexContainer} />
        </aside>}
        <main id="workspace-content" className="workspace-content">
        {me.realms.length > 0 && showRealmForm && <div id="new-realm">
          <EmptyRealmPanel creating={creatingRealm} error={realmCreationError}
            onSubmit={createRealm} onCancel={() => setShowRealmForm(false)} />
        </div>}
        {workspaceSection === 'questions' && capabilities && !runtimeReady && <RuntimeNotice capabilities={capabilities} />}
        {me.realms.length === 0 ? <EmptyRealmPanel creating={creatingRealm} error={realmCreationError} onSubmit={createRealm} /> : selectedRealm && (
          <RealmWorkspace api={api} key={selectedRealm.id} realm={selectedRealm} section={workspaceSection} indexContainer={indexContainer} onSelectEntry={() => setMobileIndexOpen(false)} onRevealIndex={() => setMobileIndexOpen(true)} />
        )}
        </main>
      </div>
    </div>
  )
}
