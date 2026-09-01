export type ModelCapabilityStatus = 'READY' | 'MODEL_MISSING' | 'RUNTIME_UNAVAILABLE' | 'NOT_CONFIGURED'

export interface ModelCapability {
  provider: string
  model: string
  available: boolean
  status: ModelCapabilityStatus
  installedModels: string[]
}

export interface RuntimeCapabilities {
  chat: ModelCapability
  embedding: ModelCapability
}
