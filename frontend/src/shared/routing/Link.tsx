import type { AnchorHTMLAttributes, MouseEvent } from 'react'

import { navigate } from './history'

/** A real anchor that navigates in place, while modified clicks keep the browser's behaviour. */
export function Link({ href, replace = false, onClick, ...anchor }: Readonly<AnchorHTMLAttributes<HTMLAnchorElement> & {
  href: string
  replace?: boolean
}>) {
  function follow(event: MouseEvent<HTMLAnchorElement>) {
    onClick?.(event)
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
    if (anchor.target && anchor.target !== '_self') return
    event.preventDefault()
    navigate(href, { replace })
  }
  return <a {...anchor} href={href} onClick={follow} />
}
