import type { AuthenticatedHttpClient } from '../../shared/api'
import type { RuntimeCapabilities } from './model'

export interface RuntimeApi {
  getCapabilities(signal?: AbortSignal): Promise<RuntimeCapabilities>
}

export class HttpRuntimeApi implements RuntimeApi {
  constructor(private readonly http: AuthenticatedHttpClient) {}
  getCapabilities(signal?: AbortSignal) {
    return this.http.request<RuntimeCapabilities>('/capabilities', { signal })
  }
}
