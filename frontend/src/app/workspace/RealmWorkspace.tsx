import { useEffect, useRef, type ReactNode, type SubmitEvent } from 'react'

import { CatalogueWorkspace } from '../../features/lore'
import { QuestionPanel } from '../../features/qa'
import {
  EvidenceDialog,
  SourceReader,
  SourcesPanel,
  useSourceReader,
  type SourceDocumentView,
} from '../../features/content'
import { RealmAccessPanel, type RealmSummary } from '../../features/realm'
import { formText } from '../../shared/lib/forms'
import { playerAudience, type VisibilityAudience } from '../../shared/lib/visibility'
import { Link, navigate } from '../../shared/routing'
import { ErrorToast } from '../../shared/ui/ErrorToast'
import { routePath, sectionLabels, sectionRoute, type WorkspaceRoute, type WorkspaceSection } from '../routes'
import { useRealmWorkspace, type WorkspaceApis } from './useRealmWorkspace'
import { WorkspaceLayout, WorkspaceNavigation } from './WorkspaceLayout'

const locationLabels: Record<WorkspaceSection, string> = { ...sectionLabels, questions: 'Consultas al archivo' }

export function RealmWorkspace({ api, realm, route, header, banner }: Readonly<{
  api: WorkspaceApis
  realm: RealmSummary
  route: WorkspaceRoute
  /** Realm identity and switching controls shown above the navigation. */
  header: ReactNode
  banner?: ReactNode
}>) {
  const workspace = useRealmWorkspace({ api, realm, processingVisible: route.section === 'sources' })
  const sourceId = route.section === 'sources' ? route.sourceId : null
  const reader = useSourceReader({
    contentApi: api.content,
    realmId: realm.id,
    sourceId,
    sources: workspace.sources,
    sourcesLoaded: workspace.sourcesLoaded,
    onError: workspace.reportError,
  })
  const fileInput = useRef<HTMLInputElement>(null)
  const sourcesPath = routePath(sectionRoute(realm.id, 'sources'))
  const { administration } = workspace
  const audience: VisibilityAudience = workspace.canEdit ? {
    viewer: 'editor',
    revealedTo: (policyId) => (administration.grantsByPolicy[policyId] ?? []).flatMap((userId) => {
      const member = administration.members.find((item) => item.userId === userId)
      return member ? [member.displayName || member.email || 'Jugador sin nombre'] : []
    }),
    policyName: (policyId) => administration.policies.find((policy) => policy.id === policyId)?.name,
  } : playerAudience

  useEffect(() => {
    const previous = document.title
    document.title = `${locationLabels[route.section]} · ${realm.name} · Codex of Realms`
    return () => { document.title = previous }
  }, [realm.name, route.section])

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
    if (!await workspace.deleteSource(source)) return
    if (workspace.openEvidence?.source.documentId === source.id) workspace.closeEvidence()
    if (sourceId === source.id) navigate(sourcesPath, { replace: true })
  }

  async function askQuestion(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const question = formText(new FormData(event.currentTarget), 'question')
    if (!question) return
    await workspace.askQuestion(question)
  }

  function layout(main: ReactNode, index?: ReactNode, selection: string | null = null) {
    return (
      <WorkspaceLayout
        header={<>{header}<WorkspaceNavigation realm={realm} section={route.section} /></>}
        banner={banner}
        index={index}
        selection={selection}
        location={locationLabels[route.section]}
      >
        {workspace.sourceWarning && <p className="workspace-notice" role="status">{workspace.sourceWarning}</p>}
        {main}
      </WorkspaceLayout>
    )
  }

  function sourcesPage() {
    const index = <SourcesPanel
      canEdit={workspace.canEdit}
      audience={audience}
      fileInput={fileInput}
      loading={!workspace.sourcesLoaded}
      policies={workspace.administration.policies}
      selectedPolicyId={workspace.administration.selectedPolicyId}
      sources={workspace.sources}
      jobs={workspace.jobs}
      selectedId={sourceId ?? undefined}
      sourceHref={(source) => routePath({ realmId: realm.id, section: 'sources', sourceId: source.id })}
      onRecover={workspace.recoverSource}
      onRetry={workspace.retrySourceJob}
      uploading={workspace.uploading}
      onDelete={deleteSource}
      onPolicyChange={workspace.administration.setSelectedPolicyId}
      onUpload={uploadSource}
    />
    let main: ReactNode
    if (reader.status === 'ready' && reader.reading) {
      main = <SourceReader evidence={reader.reading} published={workspace.sources.find((source) => source.id === sourceId)}
        audience={audience} manageAccessHref={workspace.canEdit ? routePath(sectionRoute(realm.id, 'access')) : undefined}
        onClose={() => navigate(sourcesPath)} />
    } else if (reader.status === 'loading') {
      main = <p className="reader-status" role="status">Abriendo la fuente…</p>
    } else if (reader.status === 'missing' || reader.status === 'failed') {
      main = <section className="archive-reading-empty">
        <p className="eyebrow">Biblioteca del universo</p>
        <h2>{reader.status === 'missing' ? 'Esta fuente no está disponible' : 'No se pudo abrir la fuente'}</h2>
        <p>{reader.status === 'missing' ? 'Puede que se haya retirado o que no sea visible para ti.' : 'Vuelve a intentarlo desde el índice.'}</p>
        <Link className="citation-link" href={sourcesPath}>Volver a las fuentes</Link>
      </section>
    } else {
      main = <section className="archive-reading-empty">
        <p className="eyebrow">Biblioteca del universo</p>
        <h2>Las voces de {realm.name}</h2>
        <p>Crónicas, notas y documentos que dan forma a tu mundo.</p>
        <div className="reading-prompt"><h3>{workspace.sources.length > 0 ? 'Abre una fuente del índice' : 'El archivo empieza aquí'}</h3><p>{workspace.sources.length > 0 ? 'Lee su contenido original y vuelve a él cuando necesites contrastar una afirmación.' : workspace.canEdit ? 'Añade un documento Markdown o TXT desde el índice para comenzar.' : 'Todavía no hay fuentes disponibles para ti.'}</p></div>
      </section>
    }
    return layout(<div className="source-workspace" aria-busy={!workspace.sourcesLoaded}>{main}</div>, index, sourceId)
  }

  let page: ReactNode
  switch (route.section) {
    case 'sources':
      page = sourcesPage()
      break
    case 'questions':
      page = layout(<div className="question-workspace">
        <QuestionPanel
          answer={workspace.answer}
          asking={workspace.asking}
          loadingCitation={workspace.loadingCitation}
          loadingRealm={!workspace.sourcesLoaded}
          onInspectCitation={workspace.inspectCitation}
          onCancel={workspace.cancelQuestion}
          onSubmit={askQuestion}
        />
      </div>)
      break
    case 'access':
      page = layout(workspace.canEdit && <RealmAccessPanel administration={workspace.administration} />)
      break
    case 'canon':
      page = <CatalogueWorkspace
        contentApi={api.content}
        loreApi={api.lore}
        canEdit={workspace.canEdit}
        policies={workspace.administration.policies}
        audience={audience}
        realmId={realm.id}
        sources={workspace.sources}
        location={route}
        hrefFor={(location) => routePath({ realmId: realm.id, section: 'canon', ...location })}
        onOpenEvidence={(evidence) => void workspace.inspectCatalogueEvidence(evidence)}
        layout={({ index, content, selection }) => layout(content, index, selection)}
      />
      break
  }

  return (
    <>
      {page}
      {workspace.openEvidence && (
        <EvidenceDialog evidence={workspace.openEvidence} viewer={audience.viewer} onClose={workspace.closeEvidence} />
      )}
      {workspace.error && <ErrorToast message={workspace.error} onClose={workspace.clearError} />}
    </>
  )
}
