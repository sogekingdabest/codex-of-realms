import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { HttpCodexApi } from './api'
import { App } from './App'
import { createAuthSession } from './auth'
import { runtimeConfig } from './config'
import './styles.css'

const rootElement = document.getElementById('root')
if (!rootElement) {
  throw new Error('No se encontró el contenedor raíz de la aplicación.')
}

const root = createRoot(rootElement)

void bootstrap()

async function bootstrap() {
  try {
    const session = await createAuthSession()
    const api = new HttpCodexApi(runtimeConfig.apiBaseUrl, session.getAccessToken)
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
}
