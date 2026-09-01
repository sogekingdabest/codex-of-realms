import { HttpContentApi, type ContentApi } from '../features/content'
import { HttpLoreApi, type LoreApi } from '../features/lore'
import { HttpQaApi, type QaApi } from '../features/qa'
import { HttpRealmApi, type RealmApi } from '../features/realm'
import { HttpRuntimeApi, type RuntimeApi } from '../features/runtime'
import { AuthenticatedHttpClient } from '../shared/api'

export interface ApiClients {
  realm: RealmApi
  content: ContentApi
  qa: QaApi
  lore: LoreApi
  runtime: RuntimeApi
}

export function createApiClients(baseUrl: string, getAccessToken: () => Promise<string>): ApiClients {
  const http = new AuthenticatedHttpClient(baseUrl, getAccessToken)
  return {
    realm: new HttpRealmApi(http),
    content: new HttpContentApi(http),
    qa: new HttpQaApi(http),
    lore: new HttpLoreApi(http),
    runtime: new HttpRuntimeApi(http),
  }
}
