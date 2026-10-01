import { useId } from 'react'

import type { CanonStatus } from './model'

const STAR = 'M8 3.6 L9.1 6.9 L12.4 8 L9.1 9.1 L8 12.4 L6.9 9.1 L3.6 8 L6.9 6.9 Z'

/** Small seal for lists: solid for canon, dashed for a proposal awaiting review. */
export function CanonMark({ status, showLabel = true }: Readonly<{ status: CanonStatus; showLabel?: boolean }>) {
  const label = status === 'CANON' ? 'Canon' : 'Propuesto'
  const canon = status === 'CANON'
  return (
    <span className={`canon-mark ${canon ? 'is-canon' : 'is-proposed'}`} title={showLabel ? undefined : label}>
      <svg width="14" height="14" viewBox="0 0 16 16" {...(showLabel ? { 'aria-hidden': true } : { role: 'img', 'aria-label': label })}>
        <circle cx="8" cy="8" r="7" fill="none" stroke="currentColor" strokeWidth="1.3" strokeDasharray={canon ? undefined : '2.2 2'} />
        {canon ? <path d={STAR} fill="currentColor" /> : <circle cx="8" cy="8" r="1.7" fill="currentColor" />}
      </svg>
      {showLabel && label}
    </span>
  )
}

/** The large stamp beside a ficha's title: struck at an angle once canon, dashed and level while proposed. */
export function CanonSeal({ status }: Readonly<{ status: CanonStatus }>) {
  const ringId = `canon-ring-${useId().replace(/[^\w-]/g, '')}`
  const canon = status === 'CANON'
  return (
    <svg className={`canon-seal ${canon ? 'is-canon' : 'is-proposed'}`} width="84" height="84" viewBox="0 0 100 100"
      role="img" aria-label={canon ? 'Sello de canon' : 'Sello de propuesta'}>
      <defs><path id={ringId} d="M50 50 m-36 0 a36 36 0 1 1 72 0 a36 36 0 1 1 -72 0" /></defs>
      <circle cx="50" cy="50" r="46" fill="none" stroke="currentColor" strokeWidth={canon ? 2 : 1.6} strokeDasharray={canon ? undefined : '5 4'} />
      <circle cx="50" cy="50" r="27" fill="none" stroke="currentColor" strokeWidth={canon ? 1.2 : 1} strokeDasharray={canon ? undefined : '3 3'} />
      <text className="canon-seal-ring" fill="currentColor" aria-hidden="true">
        <textPath href={`#${ringId}`}>{canon ? 'canon · archivo de campaña ·' : 'propuesta · sin revisar ·'}</textPath>
      </text>
      {canon
        ? <path d="M50 31 L53.4 46.6 L69 50 L53.4 53.4 L50 69 L46.6 53.4 L31 50 L46.6 46.6 Z" fill="currentColor" />
        : <circle cx="50" cy="50" r="5" fill="none" stroke="currentColor" strokeWidth="1.4" />}
    </svg>
  )
}
