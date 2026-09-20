import { useEffect, useRef, type KeyboardEvent, type ReactNode } from 'react'

/** Native modal semantics keep keyboard focus inside and make the background inert. */
export function Modal({ labelledBy, className, onClose, children, restoreFocusTo }: Readonly<{
  labelledBy: string
  className: string
  onClose: () => void
  children: ReactNode
  restoreFocusTo?: HTMLElement | null
}>) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current!
    const previousFocus = restoreFocusTo ?? document.activeElement
    dialog.showModal()
    dialog.querySelector<HTMLElement>('[data-modal-initial-focus]')?.focus()
    return () => {
      dialog.close()
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus()
    }
  }, [restoreFocusTo])

  function keepFocusInside(event: KeyboardEvent<HTMLDialogElement>) {
    if (event.key !== 'Tab') return
    const controls = Array.from(event.currentTarget.querySelectorAll<HTMLElement>(
      'a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
    )).filter((element) => element.getClientRects().length > 0)
    const first = controls[0]
    const last = controls.at(-1)
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last?.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first?.focus()
    }
  }

  return <dialog ref={ref} className={className} aria-labelledby={labelledBy} onKeyDown={keepFocusInside}
    onCancel={(event) => { event.preventDefault(); onClose() }}>
    {children}
  </dialog>
}
