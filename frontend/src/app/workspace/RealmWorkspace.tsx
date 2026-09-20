import { useRef, type SubmitEvent } from 'react'
import { createPortal } from 'react-dom'

import { CatalogueWorkspace } from '../../features/lore'
import { QuestionPanel } from '../../features/qa'
import {
  EvidenceDialog,
  SourcesPanel,
  type SourceDocumentView,
} from '../../features/content'
import { RealmAccessPanel, type RealmSummary } from '../../features/realm'
import { formText } from '../../shared/lib/forms'
import { ErrorToast } from '../../shared/ui/ErrorToast'
import { useRealmWorkspace, type WorkspaceApis } from './useRealmWorkspace'
import { SourceReader } from '../../features/content/EvidenceDialog'

export type WorkspaceSection = 'sources' | 'questions' | 'canon' | 'access'

export function RealmWorkspace({ api, realm, section, indexContainer, onSelectEntry, onRevealIndex }: Readonly<{
  api: WorkspaceApis
  realm: RealmSummary
  section: WorkspaceSection
  indexContainer?: HTMLElement | null
  onSelectEntry?: () => void
  onRevealIndex?: () => void
}>) {
  const workspace = useRealmWorkspace({ api, realm, processingVisible: section === 'sources' })
  const fileInput = useRef<HTMLInputElement>(null)

  async function uploadSource(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const formElement = event.currentTarget
    const title = formText(new FormData(formElement), 'title')
    const file = fileInput.current?.files?.[0]
    if (!title || !file) return
    if (await workspace.uploadSource(title, file)) formElement.reset()
  }

  async function deleteSource(source: SourceDocumentView) {
    if (!window.confirm(`¿Eliminar «${source.title}» y retirarla de las respuestas?`)) return
    if (await workspace.deleteSource(source) && workspace.openEvidence?.source.documentId === source.id) workspace.closeEvidence()
  }

  async function askQuestion(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const question = formText(new FormData(event.currentTarget), 'question')
    if (!question) return
    await workspace.askQuestion(question)
  }

  const sourceIndex = <SourcesPanel
            canEdit={workspace.canEdit}
            fileInput={fileInput}
            loading={!workspace.sourcesLoaded}
            policies={workspace.administration.policies}
            selectedPolicyId={workspace.administration.selectedPolicyId}
            sources={workspace.sources}
            jobs={workspace.jobs}
            opening={workspace.loadingCitation}
            onOpen={async (source) => { const opened = await workspace.openSource(source); if (opened) onSelectEntry?.(); return opened }}
            onRecover={workspace.recoverSource}
            onRetry={workspace.retrySourceJob}
            uploading={workspace.uploading}
            onDelete={deleteSource}
            onPolicyChange={workspace.administration.setSelectedPolicyId}
            onUpload={uploadSource}
            selectedId={workspace.openEvidence?.reference === null ? workspace.openEvidence.source.documentId : undefined}
          />

  return (
    <>
      <div className="workspace-location">{section === 'canon' ? 'Atlas del canon' : section === 'sources' ? 'Fuentes' : section === 'questions' ? 'Consultas al archivo' : 'Personas y permisos'}</div>
      {workspace.sourceWarning && <p className="workspace-notice" role="status">{workspace.sourceWarning}</p>}
      {section === 'sources' && <div className={indexContainer ? 'source-workspace' : 'source-workspace with-local-index'} aria-busy={!workspace.sourcesLoaded}>
        {indexContainer ? createPortal(sourceIndex, indexContainer) : sourceIndex}
        {workspace.openEvidence?.reference === null ? <SourceReader evidence={workspace.openEvidence} onClose={() => { onRevealIndex?.(); workspace.closeEvidence() }} /> : <section className="archive-reading-empty">
          <p className="eyebrow">Biblioteca del universo</p>
          <h2>Las voces de {realm.name}</h2>
          <p>Crónicas, notas y documentos que dan forma a tu mundo.</p>
          <div className="reading-prompt"><h3>{workspace.sources.length > 0 ? 'Abre una fuente del índice' : 'El archivo empieza aquí'}</h3><p>{workspace.sources.length > 0 ? 'Lee su contenido original y vuelve a él cuando necesites contrastar una afirmación.' : workspace.canEdit ? 'Añade un documento Markdown o TXT desde el índice para comenzar.' : 'Todavía no hay fuentes disponibles para ti.'}</p></div>
        </section>}
      </div>}
      {section === 'questions' && <div className="question-workspace">
          <QuestionPanel
            answer={workspace.answer}
            asking={workspace.asking}
            loadingCitation={workspace.loadingCitation}
            loadingRealm={!workspace.sourcesLoaded}
            onInspectCitation={workspace.inspectCitation}
            onSubmit={askQuestion}
          />
      </div>}
      {section === 'access' && workspace.canEdit && <RealmAccessPanel administration={workspace.administration} />}
      {section === 'canon' && (
        <CatalogueWorkspace
          contentApi={api.content}
          loreApi={api.lore}
          canEdit={workspace.canEdit}
          policies={workspace.administration.policies}
          realmId={realm.id}
          sources={workspace.sources}
          onOpenEvidence={(evidence) => void workspace.inspectCatalogueEvidence(evidence)}
          indexContainer={indexContainer}
          onSelectEntry={onSelectEntry}
        />
      )}
      {workspace.openEvidence?.reference && (
        <EvidenceDialog evidence={workspace.openEvidence} onClose={workspace.closeEvidence} />
      )}
      {workspace.error && <ErrorToast message={workspace.error} onClose={workspace.clearError} />}
    </>
  )
}
