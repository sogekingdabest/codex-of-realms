import { useCallback, useEffect, useRef, useState } from 'react'

import type {
  ContentApi,
  EvidenceReference,
  SourceContentView,
  SourceDocumentView,
  SourceJobView,
  SourceEvidence,
} from '../../features/content'
import type { LoreApi } from '../../features/lore'
import type { Citation, LoreAnswer, QaApi } from '../../features/qa'
import {
  useRealmAdministration,
  type RealmAdministrationApi,
  type RealmSummary,
} from '../../features/realm'
import { ApiError } from '../../shared/api'
import { errorMessage, isAbortError } from '../../shared/lib/errors'

export interface WorkspaceApis {
  readonly content: ContentApi
  readonly lore: LoreApi
  readonly qa: QaApi
  readonly realm: RealmAdministrationApi
}

export function useRealmWorkspace({ api, realm, processingVisible = true }: Readonly<{
  api: WorkspaceApis
  realm: RealmSummary
  processingVisible?: boolean
}>) {
  const canEdit = realm.role === 'OWNER' || realm.role === 'EDITOR'
  const isOwner = realm.role === 'OWNER'
  const [sources, setSources] = useState<SourceDocumentView[]>([])
  const [jobs, setJobs] = useState<SourceJobView[]>([])
  const [jobsRevision, setJobsRevision] = useState(0)
  const [sourcesLoaded, setSourcesLoaded] = useState(false)
  const initialSourcesRequest = useRef<AbortController | null>(null)
  const [answer, setAnswer] = useState<LoreAnswer | null>(null)
  const [openEvidence, setOpenEvidence] = useState<{
    reference: EvidenceReference | null
    returnFocusTo: HTMLElement | null
    source: SourceContentView
  } | null>(null)
  const [sourceWarning, setSourceWarning] = useState<string | null>(null)
  const showExclusions = (count = 0) => setSourceWarning(count > 0
    ? `${count} frase(s) superan los 2.000 caracteres y no podrán usarse para responder. Divide esas frases y vuelve a subir la fuente.`
    : null)
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [asking, setAsking] = useState(false)
  const [loadingCitation, setLoadingCitation] = useState(false)

  const reportError = useCallback((reason: unknown) => {
    setError(errorMessage(reason))
  }, [])
  const clearError = useCallback(() => setError(null), [])

  const administration = useRealmAdministration({
    api: api.realm,
    canEdit,
    isOwner,
    realmId: realm.id,
    onError: reportError,
    onOperationStart: clearError,
  })

  useEffect(() => {
    const controller = new AbortController()
    initialSourcesRequest.current = controller
    api.content.listSources(realm.id, controller.signal)
      .then((nextSources) => {
        if (!controller.signal.aborted) setSources(nextSources)
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted && !isAbortError(reason)) reportError(reason)
      })
      .finally(() => {
        if (!controller.signal.aborted) setSourcesLoaded(true)
      })
    return () => controller.abort()
  }, [api.content, realm.id, reportError])

  useEffect(() => {
    if (!canEdit || !processingVisible) return
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined
    async function refresh() {
      try {
        const nextJobs = await api.content.listSourceJobs(realm.id, controller.signal)
        if (controller.signal.aborted) return
        setJobs(nextJobs)
        const nextSources = await api.content.listSources(realm.id, controller.signal)
        if (controller.signal.aborted) return
        // A later refresh supersedes the independent initial load, even if it finishes first.
        initialSourcesRequest.current?.abort()
        setSources(nextSources)
        setSourcesLoaded(true)
        if (nextJobs.some((job) => ['UPLOADING', 'QUEUED', 'RUNNING'].includes(job.state))) {
          timer = setTimeout(() => void refresh(), 2000)
        }
      } catch (reason) {
        if (controller.signal.aborted || isAbortError(reason)) return
        reportError(reason)
        if (reason instanceof ApiError && [401, 403, 404].includes(reason.status)) {
          initialSourcesRequest.current?.abort()
          setJobs([]); setSources([])
          setSourcesLoaded(true)
        } else { timer = setTimeout(() => void refresh(), 2000) }
      }
    }
    void refresh()
    return () => { controller.abort(); clearTimeout(timer) }
  }, [api.content, realm.id, canEdit, processingVisible, jobsRevision, reportError])

  async function recoverSource(documentId: string, policyId: string, file?: File) {
    setUploading(true)
    setError(null)
    try {
      const submission = file ? await api.content.replaceSource(realm.id, documentId, policyId, file)
        : await api.content.reprocessSource(realm.id, documentId)
      showExclusions(submission.excludedSentences)
      setJobsRevision((value) => value + 1)
      return true
    } catch (reason) { reportError(reason); return false }
    finally { setUploading(false) }
  }
  async function retrySourceJob(jobId: string) {
    setUploading(true)
    setError(null)
    try {
      await api.content.retrySourceJob(realm.id, jobId)
      setJobsRevision((value) => value + 1)
    } catch (reason) { reportError(reason) }
    finally { setUploading(false) }
  }

  async function uploadSource(title: string, file: File) {
    if (!administration.selectedPolicyId) return false
    setUploading(true)
    setError(null)
    try {
      const submission = await api.content.uploadSource(
        realm.id,
        title,
        administration.selectedPolicyId,
        file,
      )
      setJobsRevision((value) => value + 1)
      showExclusions(submission.excludedSentences)
      administration.setSelectedPolicyId(administration.policies[0]?.id ?? '')
      return true
    } catch (reason) {
      reportError(reason)
      return false
    } finally {
      setUploading(false)
    }
  }

  async function deleteSource(source: SourceDocumentView) {
    setError(null)
    try {
      await api.content.deleteSource(realm.id, source.id)
      setSources((current) => current.filter((item) => item.id !== source.id))
      return true
    } catch (reason) {
      reportError(reason)
      return false
    }
  }

  async function askQuestion(question: string) {
    setAsking(true)
    setError(null)
    setAnswer(null)
    try {
      setAnswer(await api.qa.ask(realm.id, question))
      return true
    } catch (reason) {
      reportError(reason)
      return false
    } finally {
      setAsking(false)
    }
  }

  const evidenceRequest = useRef<AbortController | null>(null)
  useEffect(() => () => evidenceRequest.current?.abort(), [])

  async function readSource(documentId: string, versionId: string, reference: EvidenceReference | null) {
    const returnFocusTo = document.activeElement instanceof HTMLElement ? document.activeElement : null
    evidenceRequest.current?.abort()
    const controller = new AbortController()
    evidenceRequest.current = controller
    setLoadingCitation(true)
    setError(null)
    try {
      const source = await api.content.getSourceContent(realm.id, documentId, versionId, controller.signal)
      if (controller.signal.aborted) return false
      setOpenEvidence({
        reference,
        source,
        returnFocusTo,
      })
      return true
    } catch (reason) {
      if (!controller.signal.aborted && !isAbortError(reason)) reportError(reason)
      return false
    } finally {
      if (!controller.signal.aborted) setLoadingCitation(false)
    }
  }

  const inspectEvidence = (reference: EvidenceReference) => readSource(reference.documentId, reference.versionId, reference)
  const openSource = (source: SourceDocumentView) => readSource(source.id, source.versionId, null)

  const inspectCitation = async (citation: Citation) => {
    await inspectEvidence({
      documentId: citation.sourceDocumentId,
      versionId: citation.documentVersionId,
      heading: citation.heading,
      startOffset: citation.startOffset,
      endOffset: citation.endOffset,
      eyebrow: `Evidencia autorizada · versión ${citation.versionNumber}`,
    })
  }

  const inspectCatalogueEvidence = (evidence: SourceEvidence) => inspectEvidence({
    documentId: evidence.documentId,
    versionId: evidence.documentVersionId,
    heading: evidence.heading,
    startOffset: evidence.startOffset,
    endOffset: evidence.endOffset,
    eyebrow: `Procedencia del canon · ${evidence.checksumSha256.slice(0, 10)}`,
  })

  return {
    administration,
    answer,
    askQuestion,
    asking,
    canEdit,
    clearError,
    closeEvidence: () => setOpenEvidence(null),
    deleteSource,
    error,
    inspectCatalogueEvidence,
    inspectCitation,
    loadingCitation,
    openEvidence,
    openSource,
    sources,
    sourcesLoaded,
    jobs,
    recoverSource,
    retrySourceJob,
    uploading,
    sourceWarning,
    uploadSource,
  }
}
