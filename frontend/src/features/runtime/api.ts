import type { AuthenticatedHttpClient } from '../../shared/api'
import type { ModelWarmup, RuntimeCapabilities } from './model'

export interface RuntimeApi {
  getCapabilities(signal?: AbortSignal): Promise<RuntimeCapabilities>
  /** Asks the server to load the models in the background. */
  warmUp(): Promise<ModelWarmup>
}

export class HttpRuntimeApi implements RuntimeApi {
  constructor(private readonly http: AuthenticatedHttpClient) {}
  getCapabilities(signal?: AbortSignal) {
    return this.http.request<RuntimeCapabilities>('/capabilities', { signal })
  }
  warmUp() {
    return this.http.request<ModelWarmup>('/capabilities/warm-up', { method: 'POST' })
  }
}
