import { BannerIcon, GemIcon, HourglassIcon, PersonIcon, PinIcon } from '../../shared/ui/icons'
import type { EntityType } from './model'

const icons = {
  CHARACTER: PersonIcon,
  PLACE: PinIcon,
  FACTION: BannerIcon,
  OBJECT: GemIcon,
  EVENT: HourglassIcon,
} satisfies Record<EntityType, unknown>

export function EntityTypeIcon({ type, size = 14 }: Readonly<{ type: EntityType; size?: number }>) {
  const Icon = icons[type]
  return <Icon size={size} />
}
