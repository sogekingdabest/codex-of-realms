import type { AuthenticatedHttpClient } from '../../shared/api'
import type { LoreEntityInput, LoreEntityView, LoreRelationInput, LoreRelationUpdateInput, LoreRelationView } from './model'

export interface LoreApi {
  listLoreEntities(realmId: string, signal?: AbortSignal): Promise<LoreEntityView[]>
  createLoreEntity(realmId: string, input: LoreEntityInput): Promise<LoreEntityView>
  updateLoreEntity(realmId: string, entityId: string, input: LoreEntityInput): Promise<LoreEntityView>
  promoteLoreEntity(realmId: string, entityId: string): Promise<LoreEntityView>
  deleteLoreEntity(realmId: string, entityId: string): Promise<void>
  listLoreRelations(realmId: string, signal?: AbortSignal): Promise<LoreRelationView[]>
  createLoreRelation(realmId: string, input: LoreRelationInput): Promise<LoreRelationView>
  updateLoreRelation(realmId: string, relationId: string, input: LoreRelationUpdateInput): Promise<LoreRelationView>
  promoteLoreRelation(realmId: string, relationId: string): Promise<LoreRelationView>
  deleteLoreRelation(realmId: string, relationId: string): Promise<void>
}

export class HttpLoreApi implements LoreApi {
  constructor(private readonly http: AuthenticatedHttpClient) {}
  listLoreEntities(realmId: string, signal?: AbortSignal) { return this.http.request<LoreEntityView[]>(`/realms/${id(realmId)}/catalogue/entities`, { signal }) }
  createLoreEntity(realmId: string, input: LoreEntityInput) { return this.http.request<LoreEntityView>(`/realms/${id(realmId)}/catalogue/entities`, { method: 'POST', body: JSON.stringify(input) }) }
  updateLoreEntity(realmId: string, entityId: string, input: LoreEntityInput) { return this.http.request<LoreEntityView>(`/realms/${id(realmId)}/catalogue/entities/${id(entityId)}`, { method: 'PUT', body: JSON.stringify(input) }) }
  promoteLoreEntity(realmId: string, entityId: string) { return this.http.request<LoreEntityView>(`/realms/${id(realmId)}/catalogue/entities/${id(entityId)}/promotion`, { method: 'POST' }) }
  deleteLoreEntity(realmId: string, entityId: string) { return this.http.request<void>(`/realms/${id(realmId)}/catalogue/entities/${id(entityId)}`, { method: 'DELETE' }) }
  listLoreRelations(realmId: string, signal?: AbortSignal) { return this.http.request<LoreRelationView[]>(`/realms/${id(realmId)}/catalogue/relations`, { signal }) }
  createLoreRelation(realmId: string, input: LoreRelationInput) { return this.http.request<LoreRelationView>(`/realms/${id(realmId)}/catalogue/relations`, { method: 'POST', body: JSON.stringify(input) }) }
  updateLoreRelation(realmId: string, relationId: string, input: LoreRelationUpdateInput) { return this.http.request<LoreRelationView>(`/realms/${id(realmId)}/catalogue/relations/${id(relationId)}`, { method: 'PUT', body: JSON.stringify(input) }) }
  promoteLoreRelation(realmId: string, relationId: string) { return this.http.request<LoreRelationView>(`/realms/${id(realmId)}/catalogue/relations/${id(relationId)}/promotion`, { method: 'POST' }) }
  deleteLoreRelation(realmId: string, relationId: string) { return this.http.request<void>(`/realms/${id(realmId)}/catalogue/relations/${id(relationId)}`, { method: 'DELETE' }) }
}

const id = encodeURIComponent
