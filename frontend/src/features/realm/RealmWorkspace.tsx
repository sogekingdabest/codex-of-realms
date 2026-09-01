import { useEffect, useRef, useState, type SubmitEvent } from 'react'

import { CatalogueWorkspace, type LoreApi } from '../lore'
import { QuestionPanel, type Citation, type LoreAnswer, type QaApi } from '../qa'
import {
  EvidenceDialog,
  SourcesPanel,
  type ContentApi,
  type EvidenceReference,
  type SourceContentView,
  type SourceDocumentView,
  type SourceEvidence,
} from '../content'
import { errorMessage, isAbortError } from '../../shared/lib/errors'
import { formText } from '../../shared/lib/forms'
import { ErrorToast } from '../../shared/ui/ErrorToast'
import type { RealmApi } from './api'
import type { AccessPolicyView, InvitationView, MembershipView, RealmSummary } from './model'
import { AccessPanel } from './RealmPanels'

interface WorkspaceApis {
  realm: RealmApi
  content: ContentApi
  qa: QaApi
  lore: LoreApi
}

export function RealmWorkspace({ api, realm, section }: Readonly<{
  api: WorkspaceApis
  realm: RealmSummary
  section: 'archive' | 'canon'
}>) {
  const canEdit = realm.role === 'OWNER' || realm.role === 'EDITOR'
  const isOwner = realm.role === 'OWNER'
  const [sources, setSources] = useState<SourceDocumentView[]>([])
  const [sourcesLoaded, setSourcesLoaded] = useState(false)
  const [policies, setPolicies] = useState<AccessPolicyView[]>([])
  const [members, setMembers] = useState<MembershipView[]>([])
  const [invitations, setInvitations] = useState<InvitationView[]>([])
  const [grantsByPolicy, setGrantsByPolicy] = useState<Record<string, string[]>>({})
  const [selectedPolicyId, setSelectedPolicyId] = useState('')
  const [answer, setAnswer] = useState<LoreAnswer | null>(null)
  const [openEvidence, setOpenEvidence] = useState<{ reference: EvidenceReference; source: SourceContentView } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [creatingPolicy, setCreatingPolicy] = useState(false)
  const [inviting, setInviting] = useState(false)
  const [updatingAccess, setUpdatingAccess] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [asking, setAsking] = useState(false)
  const [loadingCitation, setLoadingCitation] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const controller = new AbortController()
    api.content.listSources(realm.id, controller.signal)
      .then(setSources)
      .catch((reason: unknown) => { if (!isAbortError(reason)) setError(errorMessage(reason)) })
      .finally(() => { if (!controller.signal.aborted) setSourcesLoaded(true) })
    return () => controller.abort()
  }, [api.content, realm.id])

  useEffect(() => {
    if (!canEdit) return
    const controller = new AbortController()
    const loadAccess = async () => {
      const [nextPolicies, nextMembers, nextInvitations] = await Promise.all([
        api.realm.listPolicies(realm.id, controller.signal),
        api.realm.listMemberships(realm.id, controller.signal),
        isOwner ? api.realm.listInvitations(realm.id, controller.signal) : Promise.resolve<InvitationView[]>([]),
      ])
      const grantEntries = await Promise.all(nextPolicies
        .filter((policy) => policy.classification === 'SPOILER')
        .map(async (policy) => [policy.id, (await api.realm.listPolicyGrants(realm.id, policy.id, controller.signal)).map((member) => member.userId)] as const))
      return { nextPolicies, nextMembers, nextInvitations, grantEntries }
    }
    loadAccess().then(({ nextPolicies, nextMembers, nextInvitations, grantEntries }) => {
      if (controller.signal.aborted) return
      setPolicies(nextPolicies)
      setMembers(nextMembers)
      setInvitations(nextInvitations)
      setGrantsByPolicy(Object.fromEntries(grantEntries))
      setSelectedPolicyId(nextPolicies[0]?.id ?? '')
    }).catch((reason: unknown) => { if (!isAbortError(reason)) setError(errorMessage(reason)) })
    return () => controller.abort()
  }, [api.realm, canEdit, isOwner, realm.id])

  async function createPolicy(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const name = formText(form, 'policyName')
    if (!name) return
    setCreatingPolicy(true); setError(null)
    try {
      const policy = await api.realm.createPolicy(realm.id, 'SPOILER', name, formText(form, 'policyDescription') || undefined)
      setPolicies((current) => [...current, policy])
      setGrantsByPolicy((current) => ({ ...current, [policy.id]: [] }))
      setSelectedPolicyId(policy.id)
      formElement.reset()
    } catch (reason) { setError(errorMessage(reason)) } finally { setCreatingPolicy(false) }
  }

  async function inviteMember(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const email = formText(form, 'memberEmail')
    if (!email) return
    setInviting(true); setError(null)
    try {
      const invitation = await api.realm.inviteMember(realm.id, email, form.get('memberRole') === 'EDITOR' ? 'EDITOR' : 'PLAYER')
      setInvitations((current) => [invitation, ...current.filter((item) => item.id !== invitation.id)])
      setMembers(await api.realm.listMemberships(realm.id))
      formElement.reset()
    } catch (reason) { setError(errorMessage(reason)) } finally { setInviting(false) }
  }

  async function removeMember(member: MembershipView) {
    if (member.role === 'OWNER' || !window.confirm(`¿Quitar a ${member.displayName} de este universo?`)) return
    setUpdatingAccess(true); setError(null)
    try {
      await api.realm.removeMembership(realm.id, member.userId)
      setMembers((current) => current.filter((item) => item.userId !== member.userId))
      setGrantsByPolicy((current) => Object.fromEntries(Object.entries(current).map(([policyId, userIds]) => [policyId, userIds.filter((userId) => userId !== member.userId)])))
    } catch (reason) { setError(errorMessage(reason)) } finally { setUpdatingAccess(false) }
  }

  async function revokeInvitation(invitation: InvitationView) {
    setUpdatingAccess(true); setError(null)
    try {
      await api.realm.revokeInvitation(realm.id, invitation.id)
      setInvitations((current) => current.map((item) => item.id === invitation.id ? { ...item, status: 'REVOKED' } : item))
    } catch (reason) { setError(errorMessage(reason)) } finally { setUpdatingAccess(false) }
  }

  async function toggleGrant(policyId: string, member: MembershipView) {
    const granted = grantsByPolicy[policyId]?.includes(member.userId) ?? false
    setUpdatingAccess(true); setError(null)
    try {
      await (granted ? api.realm.revokePolicy(realm.id, policyId, member.userId) : api.realm.grantPolicy(realm.id, policyId, member.userId))
      setGrantsByPolicy((current) => ({ ...current, [policyId]: granted ? (current[policyId] ?? []).filter((userId) => userId !== member.userId) : [...(current[policyId] ?? []), member.userId] }))
    } catch (reason) { setError(errorMessage(reason)) } finally { setUpdatingAccess(false) }
  }

  async function uploadSource(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedPolicyId || !fileInput.current?.files?.[0]) return
    const formElement = event.currentTarget
    const title = formText(new FormData(formElement), 'title')
    if (!title) return
    setUploading(true); setError(null)
    try {
      const source = await api.content.uploadSource(realm.id, title, selectedPolicyId, fileInput.current.files[0])
      setSources((current) => [source, ...current.filter((item) => item.id !== source.id)])
      formElement.reset(); setSelectedPolicyId(policies[0]?.id ?? '')
    } catch (reason) { setError(errorMessage(reason)) } finally { setUploading(false) }
  }

  async function deleteSource(source: SourceDocumentView) {
    if (!window.confirm(`¿Eliminar «${source.title}» y retirarla de las respuestas?`)) return
    setError(null)
    try { await api.content.deleteSource(realm.id, source.id); setSources((current) => current.filter((item) => item.id !== source.id)) }
    catch (reason) { setError(errorMessage(reason)) }
  }

  async function askQuestion(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const question = formText(new FormData(event.currentTarget), 'question')
    if (!question) return
    setAsking(true); setError(null); setAnswer(null)
    try { setAnswer(await api.qa.ask(realm.id, question)) }
    catch (reason) { setError(errorMessage(reason)) } finally { setAsking(false) }
  }

  async function inspectEvidence(reference: EvidenceReference) {
    setLoadingCitation(true); setError(null)
    try { setOpenEvidence({ reference, source: await api.content.getSourceContent(realm.id, reference.documentId, reference.versionId) }) }
    catch (reason) { setError(errorMessage(reason)) } finally { setLoadingCitation(false) }
  }

  const inspectCitation = (citation: Citation) => inspectEvidence({ documentId: citation.sourceDocumentId, versionId: citation.documentVersionId, heading: citation.heading, startOffset: citation.startOffset, endOffset: citation.endOffset, eyebrow: `Evidencia autorizada · versión ${citation.versionNumber}` })
  const inspectCatalogueEvidence = (evidence: SourceEvidence) => inspectEvidence({ documentId: evidence.documentId, versionId: evidence.documentVersionId, heading: evidence.heading, startOffset: evidence.startOffset, endOffset: evidence.endOffset, eyebrow: `Procedencia del canon · ${evidence.checksumSha256.slice(0, 10)}` })
  const spoilerPolicies = policies.filter((policy) => policy.classification === 'SPOILER')
  const playerMembers = members.filter((member) => member.role === 'PLAYER')

  return (
    <>
      {section === 'archive' ? (
        <div className="content-grid" aria-busy={!sourcesLoaded}>
          <SourcesPanel canEdit={canEdit} fileInput={fileInput} loading={!sourcesLoaded} policies={policies} selectedPolicyId={selectedPolicyId} sources={sources} uploading={uploading} onDelete={deleteSource} onPolicyChange={setSelectedPolicyId} onUpload={uploadSource} />
          <QuestionPanel answer={answer} asking={asking} loadingCitation={loadingCitation} loadingRealm={!sourcesLoaded} onInspectCitation={inspectCitation} onSubmit={askQuestion} />
          {canEdit && <AccessPanel creatingPolicy={creatingPolicy} grantsByPolicy={grantsByPolicy} invitations={invitations} inviting={inviting} isOwner={isOwner} members={members} playerMembers={playerMembers} spoilerPolicies={spoilerPolicies} updating={updatingAccess} onCreatePolicy={createPolicy} onInvite={inviteMember} onRemoveMember={removeMember} onRevokeInvitation={revokeInvitation} onToggleGrant={toggleGrant} />}
        </div>
      ) : (
        <CatalogueWorkspace contentApi={api.content} loreApi={api.lore} canEdit={canEdit} policies={policies} realmId={realm.id} sources={sources} onOpenEvidence={(evidence) => void inspectCatalogueEvidence(evidence)} />
      )}
      {openEvidence && <EvidenceDialog evidence={openEvidence} onClose={() => setOpenEvidence(null)} />}
      {error && <ErrorToast message={error} onClose={() => setError(null)} />}
    </>
  )
}
