import { problemMessage } from './problemMessages'

/** `message` is user-facing Spanish copy; `detail` keeps the server's diagnostic text. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string | null = null,
    readonly detail: string | null = null,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export class AuthenticatedHttpClient {
  private readonly baseUrl: string

  constructor(
    baseUrl: string,
    private readonly getAccessToken: () => Promise<string>,
  ) {
    this.baseUrl = baseUrl.replace(/\/$/, '')
  }

  async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const token = await this.getAccessToken()
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    headers.set('Authorization', `Bearer ${token}`)
    if (init.body && !(init.body instanceof FormData)) {
      headers.set('Content-Type', 'application/json')
    }

    const response = await send(this.requestUrl(path), { ...init, headers })
    if (!response.ok) {
      const problem = await readError(response)
      throw new ApiError(problemMessage(problem.code, response.status), response.status, problem.code, problem.detail)
    }
    if (response.status === 204) return undefined as T
    return (await response.json()) as T
  }

  private requestUrl(path: string): string {
    if (
      !path.startsWith('/')
      || path.startsWith('//')
      || path.includes('\\')
      || path.includes('?')
      || path.includes('#')
    ) {
      throw new Error('La ruta solicitada no es válida.')
    }

    const base = new URL(`${this.baseUrl}/`, window.location.origin)
    const basePath = base.pathname.replace(/\/$/, '')
    const url = new URL(`${basePath}${path}`, base.origin)
    if (url.origin !== base.origin || !url.pathname.startsWith(`${basePath}/`)) {
      throw new Error('La ruta solicitada queda fuera de la API configurada.')
    }

    return this.baseUrl.startsWith('/') ? url.pathname : url.toString()
  }
}

async function send(url: string, init: RequestInit) {
  try {
    return await fetch(url, init)
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    const code = 'network.unavailable'
    throw new ApiError(problemMessage(code, 0), 0, code, error instanceof Error ? error.message : null)
  }
}

async function readError(
  response: Response,
): Promise<{ detail: string | null; code: string | null }> {
  try {
    const body = (await response.json()) as {
      code?: string
      detail?: string
      title?: string
      message?: string
    }
    return {
      detail: body.detail ?? body.message ?? body.title ?? null,
      code: typeof body.code === 'string' ? body.code : null,
    }
  } catch {
    return { detail: null, code: null }
  }
}
