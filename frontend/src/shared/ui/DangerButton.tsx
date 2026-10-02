import type { ButtonHTMLAttributes } from 'react'

import { TrashIcon } from './icons'

/** Destructive actions differ from links by colour, icon and outline, not by colour alone. */
export function DangerButton({ children, className, ...button }: Readonly<ButtonHTMLAttributes<HTMLButtonElement>>) {
  return (
    <button type="button" {...button} className={className ? `danger-button ${className}` : 'danger-button'}>
      <TrashIcon size={14} />{children}
    </button>
  )
}
