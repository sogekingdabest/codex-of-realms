import type { ApiClients } from '../app/ApiClients'
import type { ContentApi } from '../features/content'
import type { LoreApi } from '../features/lore'
import type { QaApi } from '../features/qa'
import type { RealmApi } from '../features/realm'
import type { RuntimeApi } from '../features/runtime'

type FlatTestApi = RealmApi & ContentApi & QaApi & LoreApi & RuntimeApi

export function composeTestApiClients<T extends FlatTestApi>(api: T): ApiClients & T {
  return { ...api, realm: api, content: api, qa: api, lore: api, runtime: api }
}
