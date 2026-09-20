import { useEffect, useRef, type SubmitEvent } from 'react'

import type { ContentApi, SourceDocumentView } from '../../content'
import type { AccessPolicyView } from '../../realm'
import { PolicySelect } from '../CataloguePrimitives'
import { editorSubmitLabel } from '../catalogueModel'
import { EvidencePicker } from '../evidence/EvidencePicker'
import type { LoreEntityView, LoreRelationInput } from '../model'

interface RelationEditorProps {
  readonly contentApi: ContentApi
  readonly draft: LoreRelationInput
  readonly editing: boolean
  readonly entities: LoreEntityView[]
  readonly policies: AccessPolicyView[]
  readonly realmId: string
  readonly saving: boolean
  readonly sources: SourceDocumentView[]
  readonly onCancel: () => void
  readonly onChange: (draft: LoreRelationInput) => void
  readonly onSubmit: (event: SubmitEvent<HTMLFormElement>) => void
}

export function RelationEditor({ contentApi, draft, editing, entities, policies, realmId, saving, sources, onCancel, onChange, onSubmit }: RelationEditorProps) {
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => { heading.current?.focus() }, [])
  return (
    <form className="catalogue-editor" onSubmit={onSubmit}>
      <div className="catalogue-editor-heading">
        <div>
          <p className="eyebrow">{editing ? 'Revisión manual' : 'Nuevo vínculo'}</p>
          <h3 ref={heading} tabIndex={-1}>{editing ? 'Editar relación' : 'Relacionar conceptos'}</h3>
        </div>
        <button className="quiet-button" disabled={saving} type="button" onClick={onCancel}>Cancelar</button>
      </div>
      <label>
        <span>Origen</span>
        <select disabled={editing} value={draft.sourceEntityId} onChange={(event) => onChange({ ...draft, sourceEntityId: event.target.value })}>
          {entities.map((entity) => <option key={entity.id} value={entity.id}>{entity.displayName}</option>)}
        </select>
      </label>
      <label>
        <span>Destino</span>
        <select disabled={editing} value={draft.targetEntityId} onChange={(event) => onChange({ ...draft, targetEntityId: event.target.value })}>
          {entities.filter((entity) => entity.id !== draft.sourceEntityId).map((entity) => (
            <option key={entity.id} value={entity.id}>{entity.displayName}</option>
          ))}
        </select>
      </label>
      <label>
        <span>Tipo de relación</span>
        <input required maxLength={64} value={draft.relationType} onChange={(event) => onChange({ ...draft, relationType: event.target.value })} placeholder="Protege, vive en, pertenece a…" />
      </label>
      <label>
        <span>Descripción</span>
        <textarea required rows={4} maxLength={2000} value={draft.description} onChange={(event) => onChange({ ...draft, description: event.target.value })} />
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
      <button disabled={saving || draft.sourceEntityId === draft.targetEntityId} type="submit">
        {editorSubmitLabel(saving, editing)}
      </button>
    </form>
  )
}
