/** Who may read a record, as the API reports it for the current member. */
export type Visibility = 'PUBLIC' | 'GM_ONLY' | 'SPOILER'

/** Owners and editors see who a spoiler was revealed to; players see that it was revealed to them. */
export type VisibilityViewer = 'editor' | 'player'

export interface VisibilityAudience {
  readonly viewer: VisibilityViewer
  /** Display names of the players a spoiler policy has been revealed to (editors only). */
  readonly revealedTo: (policyId: string) => readonly string[]
  /** Name of an access policy, when the viewer can see policies (editors only). */
  readonly policyName: (policyId: string) => string | undefined
}

export const playerAudience: VisibilityAudience = {
  viewer: 'player',
  revealedTo: () => [],
  policyName: () => undefined,
}

/**
 * How a visibility choice reads in every editor form (upload, atlas): "Público", "Solo dirección" or
 * "Spoiler · <group>". A base policy named differently keeps its name after the kind.
 */
export function visibilityOptionLabel(option: { readonly classification: Visibility; readonly name: string }) {
  const kind = option.classification === 'SPOILER' ? 'Spoiler' : visibilityLabel(option.classification, 'editor')
  return option.name === kind ? kind : `${kind} · ${option.name}`
}

export function visibilityLabel(visibility: Visibility, viewer: VisibilityViewer, revealedTo: readonly string[] = []) {
  if (visibility === 'PUBLIC') return 'Público'
  if (visibility === 'GM_ONLY') return 'Solo dirección'
  if (viewer === 'player') return 'Revelado para ti'
  if (revealedTo.length === 0) return 'Spoiler · sin revelar'
  const named = revealedTo.slice(0, 2).join(', ')
  return revealedTo.length > 2 ? `Spoiler · ${named} y ${revealedTo.length - 2} más` : `Spoiler · ${named}`
}
