import type { CatalogueLocation } from '../features/lore'
import type { RealmSummary } from '../features/realm'

export type WorkspaceSection = 'sources' | 'canon' | 'questions' | 'access'

export type WorkspaceRoute =
  | { readonly realmId: string; readonly section: 'sources'; readonly sourceId: string | null }
  | ({ readonly realmId: string; readonly section: 'canon' } & CatalogueLocation)
  | { readonly realmId: string; readonly section: 'questions' | 'access' }

export const sectionLabels: Record<WorkspaceSection, string> = {
  canon: 'Atlas del canon',
  sources: 'Fuentes',
  questions: 'Consultas',
  access: 'Personas y permisos',
}

const REALMS = 'universos'
const RELATIONS = 'relaciones'
const slugs: Record<WorkspaceSection, string> = {
  sources: 'fuentes',
  canon: 'atlas',
  questions: 'consultas',
  access: 'personas',
}

/** `/universos/:realmId/(fuentes[/:sourceId] | atlas[/relaciones | /:entityId] | consultas | personas)` */
export function parseRoute(pathname: string): WorkspaceRoute | null {
  let segments: string[]
  try {
    segments = pathname.split('/').filter(Boolean).map(decodeURIComponent)
  } catch {
    return null
  }
  const [root, realmId, slug = slugs.sources, item, ...rest] = segments
  const section = (Object.keys(slugs) as WorkspaceSection[]).find((key) => slugs[key] === slug)
  if (root !== REALMS || !realmId || !section || rest.length > 0) return null
  switch (section) {
    case 'sources':
      return { realmId, section, sourceId: item ?? null }
    case 'canon':
      return item === RELATIONS
        ? { realmId, section, view: 'relations', entityId: null }
        : { realmId, section, view: 'entities', entityId: item ?? null }
    default:
      return item === undefined ? { realmId, section } : null
  }
}

export function routePath(route: WorkspaceRoute): string {
  const base = `/${REALMS}/${encodeURIComponent(route.realmId)}/${slugs[route.section]}`
  const item = route.section === 'sources' ? route.sourceId
    : route.section === 'canon' ? (route.view === 'relations' ? RELATIONS : route.entityId)
      : null
  return item ? `${base}/${encodeURIComponent(item)}` : base
}

export function sectionRoute(realmId: string, section: WorkspaceSection): WorkspaceRoute {
  switch (section) {
    case 'sources': return { realmId, section, sourceId: null }
    case 'canon': return { realmId, section, view: 'entities', entityId: null }
    default: return { realmId, section }
  }
}

export function canOpenSection(role: RealmSummary['role'], section: WorkspaceSection) {
  return section !== 'access' || role !== 'PLAYER'
}

/**
 * The route the app shows for a requested path: unknown or inaccessible realms fall back to the
 * first realm, and sections the member cannot open fall back to that realm's sources.
 */
export function resolveRoute(requested: WorkspaceRoute | null, realms: readonly RealmSummary[]): WorkspaceRoute | null {
  const realm = requested ? realms.find((item) => item.id === requested.realmId) : undefined
  if (!requested || !realm) return realms[0] ? sectionRoute(realms[0].id, 'sources') : null
  return canOpenSection(realm.role, requested.section) ? requested : sectionRoute(realm.id, 'sources')
}
