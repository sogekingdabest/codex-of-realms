import { useEffect, useState, type SubmitEvent } from 'react'

import type { AuthSession } from '../shared/auth'
import { errorMessage, isAbortError } from '../shared/lib/errors'
import { formText } from '../shared/lib/forms'
import { Link, navigate, usePathname } from '../shared/routing'
import { EmptyRealmPanel, roleLabels, type CurrentUserView } from '../features/realm'
import { RuntimeNotice, type RuntimeCapabilities } from '../features/runtime'
import type { ApiClients } from './ApiClients'
import { parseRoute, resolveRoute, routePath, sectionRoute } from './routes'
import { RealmWorkspace } from './workspace/RealmWorkspace'

interface AppProps {
  readonly api: ApiClients
  readonly session: AuthSession
}

export function App({ api, session }: AppProps) {
  const pathname = usePathname()
  const [me, setMe] = useState<CurrentUserView | null>(null)
  const [capabilities, setCapabilities] = useState<RuntimeCapabilities | null>(null)
  const [initialError, setInitialError] = useState<string | null>(null)
  const [creatingRealm, setCreatingRealm] = useState(false)
  const [realmCreationError, setRealmCreationError] = useState<string | null>(null)
  const [showRealmForm, setShowRealmForm] = useState(false)
  const route = me ? resolveRoute(parseRoute(pathname), me.realms) : null
  const realm = route ? me?.realms.find((item) => item.id === route.realmId) : undefined
  const runtimeReady = capabilities?.chat.available && capabilities.embedding.available
  const canonicalPath = route ? routePath(route) : '/'

  useEffect(() => {
    const controller = new AbortController()
    api.realm.getCurrentUser(controller.signal)
      .then((currentUser) => { if (!controller.signal.aborted) setMe(currentUser) })
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

  // Opening Consultas loads the models in the background, before the first question needs them.
  const questionsOpen = route?.section === 'questions'
  useEffect(() => {
    if (questionsOpen && runtimeReady) api.runtime.warmUp().catch(() => undefined)
  }, [questionsOpen, runtimeReady, api.runtime])

  // Unknown, stale or forbidden addresses are replaced by the place actually shown. The effect can
  // run after a link was followed, so it only replaces the address this render resolved.
  useEffect(() => {
    if (me && pathname !== canonicalPath) navigate(canonicalPath, { replace: true, from: pathname })
  }, [me, pathname, canonicalPath])

  async function createRealm(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const formElement = event.currentTarget
    const name = formText(new FormData(formElement), 'realmName')
    if (!name || creatingRealm) return
    setCreatingRealm(true)
    setRealmCreationError(null)
    try {
      const created = await api.realm.createRealm(name)
      setMe((current) => current ? { ...current, realms: [...current.realms, created] } : current)
      formElement.reset()
      setShowRealmForm(false)
      navigate(routePath(sectionRoute(created.id, 'sources')))
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

  const newRealmButton = (
    <button type="button" className="quiet-button new-realm-button" aria-expanded={showRealmForm}
      aria-controls="new-realm" disabled={creatingRealm}
      onClick={() => { setShowRealmForm((current) => !current); setRealmCreationError(null) }}>Nuevo universo</button>
  )
  // Players rarely start a campaign of their own, so the button waits inside the realm switcher.
  const playing = realm?.role === 'PLAYER'
  const realmControls = realm && route && (
    <div className="realm-controls">
      <p className="eyebrow">Archivo de campaña</p>
      <h1>{realm.name}</h1>
      <details className="realm-switcher"><summary>{roleLabels[realm.role]} · Cambiar universo</summary><label className="realm-picker"><span>Universo activo</span><select aria-label="Universo activo" value={realm.id} onChange={(event) => { setShowRealmForm(false); navigate(routePath(sectionRoute(event.target.value, route.section))) }}>{me.realms.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>{playing && newRealmButton}</details>
      {!playing && newRealmButton}
    </div>
  )

  return (
    <div className="app-shell">
      <a className="skip-link" href="#workspace-content">Saltar al contenido</a>
      <header className="topbar">
        <Link className="brand" href="/" aria-label="Codex of Realms, inicio">Codex of Realms</Link>
        <div className="identity"><span>{me.user.displayName || session.displayName}</span><button className="quiet-button" type="button" onClick={() => void session.logout()}>Cerrar sesión</button></div>
      </header>
      {realm && route ? (
        <RealmWorkspace
          key={realm.id}
          api={api}
          realm={realm}
          route={route}
          header={realmControls}
          banner={<>
            {showRealmForm && <div id="new-realm">
              <EmptyRealmPanel creating={creatingRealm} error={realmCreationError}
                onSubmit={createRealm} onCancel={() => setShowRealmForm(false)} />
            </div>}
            {route.section === 'questions' && capabilities && !runtimeReady && <RuntimeNotice capabilities={capabilities} />}
          </>}
        />
      ) : (
        <div className="workspace">
          <main id="workspace-content" className="workspace-content">
            <EmptyRealmPanel creating={creatingRealm} error={realmCreationError} onSubmit={createRealm} />
          </main>
        </div>
      )}
    </div>
  )
}
