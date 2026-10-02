import { visibilityLabel, type Visibility, type VisibilityViewer } from '../lib/visibility'
import { EyeIcon, GlobeIcon, LockIcon } from './icons'

const variants: Record<Visibility, string> = {
  PUBLIC: 'visibility-public',
  GM_ONLY: 'visibility-gm',
  SPOILER: 'visibility-spoiler',
}

const icons = { PUBLIC: GlobeIcon, GM_ONLY: LockIcon, SPOILER: EyeIcon } as const satisfies Record<Visibility, unknown>

/**
 * Public records stay quiet, Game Master notes are a solid amber chip and spoilers an indigo
 * outline, so the three differ in form and lightness as well as colour.
 */
export function VisibilityMark({ visibility, viewer, revealedTo, compact = false }: Readonly<{
  visibility?: Visibility
  viewer: VisibilityViewer
  revealedTo?: readonly string[]
  /** Icon only, for dense indexes; the label stays available to assistive technology. */
  compact?: boolean
}>) {
  if (!visibility || !(visibility in variants)) return null
  const label = visibilityLabel(visibility, viewer, revealedTo)
  const Icon = icons[visibility]
  const className = `visibility-mark ${variants[visibility]}${compact ? ' is-compact' : ''}`
  if (compact) return <span className={className} title={label}><Icon size={12} label={label} /></span>
  return <span className={className}><Icon size={13} />{label}</span>
}
