import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { createApiClients } from './app/ApiClients'
import { runtimeConfig } from './app/config'
import { App } from './app/App'
import { createAuthSession } from './shared/auth'
import './shared/styles/index.css'

const rootElement = document.getElementById('root')
if (!rootElement) {
  throw new Error('No se encontró el contenedor raíz de la aplicación.')
}

const root = createRoot(rootElement)

try {
  const session = await createAuthSession(runtimeConfig)
  const api = createApiClients(runtimeConfig.apiBaseUrl, session.getAccessToken)
  root.render(
    <StrictMode>
      <App api={api} session={session} />
    </StrictMode>,
  )
} catch (error) {
  const message = error instanceof Error ? error.message : 'Error de autenticación'
  root.render(
    <main className="startup-error">
      <p className="eyebrow">No hemos podido abrir el archivo</p>
      <h1>La autenticación no está disponible</h1>
      <p>{message}</p>
      <button type="button" onClick={() => window.location.reload()}>
        Reintentar
      </button>
    </main>,
  )
}
