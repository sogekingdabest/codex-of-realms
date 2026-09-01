import type { AuthenticatedHttpClient } from '../../shared/api'
import type { LoreAnswer } from './model'

export interface QaApi {
  ask(realmId: string, question: string): Promise<LoreAnswer>
}

export class HttpQaApi implements QaApi {
  constructor(private readonly http: AuthenticatedHttpClient) {}
  ask(realmId: string, question: string) {
    return this.http.request<LoreAnswer>(`/realms/${encodeURIComponent(realmId)}/questions`, {
      method: 'POST',
      body: JSON.stringify({ question }),
    })
  }
}
