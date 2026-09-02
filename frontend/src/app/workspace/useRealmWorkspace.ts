import { useCallback, useEffect, useState } from 'react'

import type {
  ContentApi,
  EvidenceReference,
  SourceContentView,
  SourceDocumentView,
  SourceEvidence,
} from '../../features/content'
import type { LoreApi } from '../../features/lore'
import type { Citation, LoreAnswer, QaApi } from '../../features/qa'
import {
  useRealmAdministration,
  type RealmApi,
  type RealmSummary,
} from '../../features/realm'
import { errorMessage, isAbortError } from '../../shared/lib/errors'

export interface WorkspaceApis {
  readonly content: ContentApi
  readonly lore: LoreApi
  readonly qa: QaApi
  readonly realm: RealmApi
}

export function useRealmWorkspace({ api, realm }: Readonly<{
  api: WorkspaceApis
  realm: RealmSummary
}>) {
  const canEdit = realm.role === 'OWNER' || realm.role === 'EDITOR'
  const isOwner = realm.role === 'OWNER'
  const [sources, setSources] = useState<SourceDocumentView[]>([])
  const [sourcesLoaded, setSourcesLoaded] = useState(false)
  const [answer, setAnswer] = useState<LoreAnswer | null>(null)
  const [openEvidence, setOpenEvidence] = useState<{
    reference: EvidenceReference
    source: SourceContentView
  } | null>(null)
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
    api.content.listSources(realm.id, controller.signal)
      .then((nextSources) => {
        if (!controller.signal.aborted) setSources(nextSources)
      })
      .catch((reason: unknown) => {
        if (!isAbortError(reason)) reportError(reason)
      })
      .finally(() => {
        if (!controller.signal.aborted) setSourcesLoaded(true)
      })
    return () => controller.abort()
  }, [api.content, realm.id, reportError])

  async function uploadSource(title: string, file: File) {
    if (!administration.selectedPolicyId) return false
    setUploading(true)
    setError(null)
    try {
      const source = await api.content.uploadSource(
        realm.id,
        title,
        administration.selectedPolicyId,
        file,
      )
      setSources((current) => [source, ...current.filter((item) => item.id !== source.id)])
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

  async function inspectEvidence(reference: EvidenceReference) {
    setLoadingCitation(true)
    setError(null)
    try {
      setOpenEvidence({
        reference,
        source: await api.content.getSourceContent(realm.id, reference.documentId, reference.versionId),
      })
      return true
    } catch (reason) {
      reportError(reason)
      return false
    } finally {
      setLoadingCitation(false)
    }
  }

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
    sources,
    sourcesLoaded,
    uploading,
    uploadSource,
  }
}
