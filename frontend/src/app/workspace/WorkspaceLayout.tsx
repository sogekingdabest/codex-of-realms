import { useEffect, useId, useRef, useState, type ReactNode } from 'react'

import type { RealmSummary } from '../../features/realm'
import { Link } from '../../shared/routing'
import { canOpenSection, routePath, sectionLabels, sectionRoute, type WorkspaceSection } from '../routes'

/** Matches the single-column layout in shared/styles/responsive.css. */
const compactLayout = '(max-width: 640px)'

/**
 * The workspace zones: the sidebar holds the realm header and, for browsable sections, an index;
 * the main zone holds what is being read. Each section fills the zones it needs.
 */
export function WorkspaceLayout({ header, index, selection = null, location, banner, children }: Readonly<{
  header: ReactNode
  index?: ReactNode
  /** Identifies what the main zone shows; on small screens the index folds away when it changes. */
  selection?: string | null
  location: ReactNode
  banner?: ReactNode
  children: ReactNode
}>) {
  const main = useRef<HTMLElement>(null)
  useEffect(() => {
    // On a phone the realm header and the index sit above the content: bring what was opened to the top.
    if (selection !== null && window.matchMedia?.(compactLayout).matches) main.current?.scrollIntoView?.({ block: 'start' })
  }, [selection])
  return (
    <div className="workspace has-realm">
      <aside className="archive-sidebar" aria-label="Índice del archivo">
        <div className="archive-sidebar-content">
          {header}
          {index && <FoldingIndex selection={selection}>{index}</FoldingIndex>}
        </div>
      </aside>
      <main ref={main} id="workspace-content" className="workspace-content">
        {banner}
        <div className="workspace-location">{location}</div>
        {children}
      </main>
    </div>
  )
}

function FoldingIndex({ selection, children }: Readonly<{ selection: string | null; children: ReactNode }>) {
  const id = useId()
  const [fold, setFold] = useState({ selection, open: selection === null })
  if (fold.selection !== selection) setFold({ selection, open: selection === null })
  return (
    <>
      <button className="mobile-index-toggle quiet-button" type="button" aria-expanded={fold.open} aria-controls={id}
        onClick={() => setFold((current) => ({ ...current, open: !current.open }))}>
        {fold.open ? 'Cerrar índice' : 'Abrir índice'}
      </button>
      <div id={id} className={`archive-index${fold.open ? ' is-open' : ''}`}>{children}</div>
    </>
  )
}

export function WorkspaceNavigation({ realm, section }: Readonly<{ realm: RealmSummary; section: WorkspaceSection }>) {
  const sections = (['canon', 'sources', 'questions', 'access'] as const).filter((item) => canOpenSection(realm.role, item))
  return (
    <nav className="workspace-navigation" aria-label="Espacio de trabajo">
      {sections.map((item) => (
        <Link key={item} href={routePath(sectionRoute(realm.id, item))}
          className={item === section ? 'active' : undefined} aria-current={item === section ? 'page' : undefined}>
          {sectionLabels[item]}
        </Link>
      ))}
    </nav>
  )
}
