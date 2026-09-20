import { useEffect, useRef, type SubmitEvent } from 'react'

import type { ContentApi, SourceDocumentView } from '../../content'
import type { AccessPolicyView } from '../../realm'
import { PolicySelect } from '../CataloguePrimitives'
import { editorSubmitLabel, entityTypeLabels } from '../catalogueModel'
import { EvidencePicker } from '../evidence/EvidencePicker'
import type { EntityType, LoreEntityInput } from '../model'

interface EntityEditorProps {
  readonly contentApi: ContentApi
  readonly draft: LoreEntityInput
  readonly editing: boolean
  readonly policies: AccessPolicyView[]
  readonly realmId: string
  readonly saving: boolean
  readonly sources: SourceDocumentView[]
  readonly onCancel: () => void
  readonly onChange: (draft: LoreEntityInput) => void
  readonly onSubmit: (event: SubmitEvent<HTMLFormElement>) => void
}

export function EntityEditor({ contentApi, draft, editing, policies, realmId, saving, sources, onCancel, onChange, onSubmit }: EntityEditorProps) {
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => { heading.current?.focus() }, [])
  return (
    <form className="catalogue-editor" onSubmit={onSubmit}>
      <div className="catalogue-editor-heading">
        <div>
          <p className="eyebrow">{editing ? 'Revisión manual' : 'Nueva ficha'}</p>
          <h3 ref={heading} tabIndex={-1}>{editing ? 'Editar concepto' : 'Registrar concepto'}</h3>
        </div>
        <button className="quiet-button" disabled={saving} type="button" onClick={onCancel}>Cancelar</button>
      </div>
      <label>
        <span>Tipo</span>
        <select value={draft.type} onChange={(event) => onChange({ ...draft, type: event.target.value as EntityType })}>
          {Object.entries(entityTypeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
      <label>
        <span>Nombre</span>
        <input required maxLength={160} value={draft.displayName} onChange={(event) => onChange({ ...draft, displayName: event.target.value })} />
      </label>
      <label>
        <span>Alias separados por comas</span>
        <input
          maxLength={1200}
          value={draft.aliases.join(', ')}
          onChange={(event) => onChange({
            ...draft,
            aliases: event.target.value.split(',').map((alias) => alias.trim()).filter(Boolean),
          })}
        />
      </label>
      <label>
        <span>Descripción</span>
        <textarea required rows={5} maxLength={4000} value={draft.description} onChange={(event) => onChange({ ...draft, description: event.target.value })} />
      </label>
      <PolicySelect policies={policies} value={draft.accessPolicyId} onChange={(accessPolicyId) => onChange({ ...draft, accessPolicyId, evidenceChunkIds: [] })} />
      <EvidencePicker
        contentApi={contentApi}
        evidenceChunkIds={draft.evidenceChunkIds}
        policyId={draft.accessPolicyId}
        realmId={realmId}
        sources={sources}
        onChange={(evidenceChunkIds) => onChange({ ...draft, evidenceChunkIds })}
      />
      <button disabled={saving || !draft.accessPolicyId} type="submit">
        {editorSubmitLabel(saving, editing)}
      </button>
    </form>
  )
}
