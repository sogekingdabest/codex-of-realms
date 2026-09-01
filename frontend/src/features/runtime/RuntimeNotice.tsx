import type { ModelCapabilityStatus, RuntimeCapabilities } from './model'

export function RuntimeNotice({ capabilities }: Readonly<{ capabilities: RuntimeCapabilities }>) {
  const unavailableCapabilities = [
    { label: 'Embeddings', capability: capabilities.embedding },
    { label: 'Respuestas', capability: capabilities.chat },
  ].filter(({ capability }) => !capability.available)
  const missingModels = [...new Set(unavailableCapabilities
    .filter(({ capability }) => capability.status === 'MODEL_MISSING')
    .map(({ capability }) => capability.model.trim()).filter(Boolean))]
  const runtimeUnavailable = unavailableCapabilities.some(({ capability }) => capability.status === 'RUNTIME_UNAVAILABLE')
  const notConfigured = unavailableCapabilities.some(({ capability }) => capability.status === 'NOT_CONFIGURED')

  return (
    <output className="runtime-notice">
      <div>
        <strong>El runtime local necesita preparación</strong>
        {unavailableCapabilities.map(({ label, capability }) => (
          <span key={label}>{label}: {capability.model || 'sin modelo'} ({runtimeStatusLabels[capability.status]}).</span>
        ))}
        {runtimeUnavailable && <span>Ollama no responde. Inicia o revisa el runtime local.</span>}
        {notConfigured && <span>Configura el proveedor y el modelo correspondientes antes de continuar.</span>}
      </div>
      {missingModels.length > 0 && (
        <div className="runtime-commands" aria-label="Modelos pendientes">
          {missingModels.map((model) => <code key={model}>ollama pull {model}</code>)}
        </div>
      )}
    </output>
  )
}

const runtimeStatusLabels: Record<ModelCapabilityStatus, string> = {
  READY: 'disponible', MODEL_MISSING: 'modelo no instalado',
  RUNTIME_UNAVAILABLE: 'runtime no disponible', NOT_CONFIGURED: 'sin configurar',
}
