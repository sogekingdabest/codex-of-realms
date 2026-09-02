import { useEffect, useState, type SubmitEvent } from 'react'

import type { AuthSession } from '../shared/auth'
import { errorMessage, isAbortError } from '../shared/lib/errors'
import { formText } from '../shared/lib/forms'
import { EmptyRealmPanel, type CurrentUserView, type RealmSummary } from '../features/realm'
import { RuntimeNotice, type RuntimeCapabilities } from '../features/runtime'
import type { ApiClients } from './ApiClients'
import { RealmWorkspace } from './workspace/RealmWorkspace'

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
  const [workspaceSection, setWorkspaceSection] = useState<'archive' | 'canon'>('archive')
  const [capabilities, setCapabilities] = useState<RuntimeCapabilities | null>(null)
  const [initialError, setInitialError] = useState<string | null>(null)
  const [creatingRealm, setCreatingRealm] = useState(false)
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
    if (!name) return
    setCreatingRealm(true)
    setInitialError(null)
    try {
      const realm = await api.realm.createRealm(name)
      setMe((current) => current ? { ...current, realms: [...current.realms, realm] } : current)
      setSelectedRealmId(realm.id)
      formElement.reset()
    } catch (reason) {
      setInitialError(errorMessage(reason))
    } finally {
      setCreatingRealm(false)
    }
  }

  if (initialError) {
    return <main className="startup-error"><p className="eyebrow">Sesión iniciada</p><h1>No pudimos cargar tu archivo</h1><p>{initialError}</p><button type="button" onClick={() => window.location.reload()}>Reintentar</button></main>
  }
  if (!me) {
    return <main className="loading-screen" aria-live="polite"><span className="sigil" aria-hidden="true"><span>C</span></span><p>Abriendo el archivo…</p></main>
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="Codex of Realms, inicio"><span className="brand-mark" aria-hidden="true"><span>C</span></span><span><strong>Codex</strong><small>of Realms</small></span></a>
        <div className="identity"><span><small>Conectado como</small><strong>{me.user.displayName || session.displayName}</strong></span><button className="quiet-button" type="button" onClick={() => void session.logout()}>Cerrar sesión</button></div>
      </header>
      <main className="workspace">
        <section className="hero">
          <div><p className="eyebrow">Archivo vivo · evidencia antes que elocuencia</p><h1>Consulta tu mundo.<br /><em>Conserva su verdad.</em></h1></div>
          {me.realms.length > 0 && <label className="realm-picker"><span>Universo activo</span><select value={selectedRealmId} onChange={(event) => setSelectedRealmId(event.target.value)}>{me.realms.map((realm) => <option key={realm.id} value={realm.id}>{realm.name}</option>)}</select>{selectedRealm && <small>{roleLabels[selectedRealm.role]}</small>}</label>}
        </section>

        {workspaceSection === 'archive' && capabilities && !runtimeReady && <RuntimeNotice capabilities={capabilities} />}
        {me.realms.length === 0 ? <EmptyRealmPanel creating={creatingRealm} onSubmit={createRealm} /> : selectedRealm && (
          <>
            <nav className="workspace-navigation" aria-label="Espacio de trabajo">
              <button className={workspaceSection === 'archive' ? 'active' : ''} type="button" onClick={() => setWorkspaceSection('archive')}>Archivo y consultas</button>
              <button className={workspaceSection === 'canon' ? 'active' : ''} type="button" onClick={() => setWorkspaceSection('canon')}>Atlas del canon</button>
            </nav>
            <RealmWorkspace api={api} key={selectedRealm.id} realm={selectedRealm} section={workspaceSection} />
          </>
        )}
      </main>
    </div>
  )
}
