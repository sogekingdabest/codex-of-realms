import { useSyncExternalStore } from 'react'

/**
 * Minimal path-based navigation over the History API. The route table belongs to the app;
 * this module only knows how to read and change `location.pathname`.
 */
const listeners = new Set<() => void>()

function subscribe(listener: () => void) {
  listeners.add(listener)
  window.addEventListener('popstate', listener)
  return () => {
    listeners.delete(listener)
    window.removeEventListener('popstate', listener)
  }
}

function currentPathname() {
  return window.location.pathname
}

export function usePathname() {
  return useSyncExternalStore(subscribe, currentPathname)
}

/**
 * With `from`, the change only happens while the address is still `from`. A correction computed
 * for an older address then cannot undo a link the user followed in the meantime.
 */
export function navigate(path: string, { replace = false, from }: { replace?: boolean; from?: string } = {}) {
  const current = currentPathname()
  if (path === current || (from !== undefined && from !== current)) return
  if (replace) window.history.replaceState(null, '', path)
  else window.history.pushState(null, '', path)
  for (const listener of listeners) listener()
}
