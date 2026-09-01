export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string | null = null,
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

    const response = await fetch(this.requestUrl(path), { ...init, headers })
    if (!response.ok) {
      const problem = await readError(response)
      throw new ApiError(problem.message, response.status, problem.code)
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

async function readError(
  response: Response,
): Promise<{ message: string; code: string | null }> {
  try {
    const body = (await response.json()) as {
      code?: string
      detail?: string
      title?: string
      message?: string
    }
    return {
      message: body.detail ?? body.message ?? body.title ?? `Error HTTP ${response.status}`,
      code: typeof body.code === 'string' ? body.code : null,
    }
  } catch {
    return { message: `Error HTTP ${response.status}`, code: null }
  }
}
