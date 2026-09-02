import { useRef, type SubmitEvent } from 'react'

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

export function RealmWorkspace({ api, realm, section }: Readonly<{
  api: WorkspaceApis
  realm: RealmSummary
  section: 'archive' | 'canon'
}>) {
  const workspace = useRealmWorkspace({ api, realm })
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
    await workspace.deleteSource(source)
  }

  async function askQuestion(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const question = formText(new FormData(event.currentTarget), 'question')
    if (!question) return
    await workspace.askQuestion(question)
  }

  return (
    <>
      {section === 'archive' ? (
        <div className="content-grid" aria-busy={!workspace.sourcesLoaded}>
          <SourcesPanel
            canEdit={workspace.canEdit}
            fileInput={fileInput}
            loading={!workspace.sourcesLoaded}
            policies={workspace.administration.policies}
            selectedPolicyId={workspace.administration.selectedPolicyId}
            sources={workspace.sources}
            uploading={workspace.uploading}
            onDelete={deleteSource}
            onPolicyChange={workspace.administration.setSelectedPolicyId}
            onUpload={uploadSource}
          />
          <QuestionPanel
            answer={workspace.answer}
            asking={workspace.asking}
            loadingCitation={workspace.loadingCitation}
            loadingRealm={!workspace.sourcesLoaded}
            onInspectCitation={workspace.inspectCitation}
            onSubmit={askQuestion}
          />
          {workspace.canEdit && (
            <RealmAccessPanel administration={workspace.administration} />
          )}
        </div>
      ) : (
        <CatalogueWorkspace
          contentApi={api.content}
          loreApi={api.lore}
          canEdit={workspace.canEdit}
          policies={workspace.administration.policies}
          realmId={realm.id}
          sources={workspace.sources}
          onOpenEvidence={(evidence) => void workspace.inspectCatalogueEvidence(evidence)}
        />
      )}
      {workspace.openEvidence && (
        <EvidenceDialog evidence={workspace.openEvidence} onClose={workspace.closeEvidence} />
      )}
      {workspace.error && <ErrorToast message={workspace.error} onClose={workspace.clearError} />}
    </>
  )
}
