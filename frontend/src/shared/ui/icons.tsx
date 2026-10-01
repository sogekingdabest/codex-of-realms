import type { ReactNode } from 'react'

interface IconProps {
  readonly size?: number
  /** Accessible name; without it the icon is decorative. */
  readonly label?: string
  readonly strokeWidth?: number
}

function StrokeIcon({ size = 16, label, strokeWidth = 1.7, children }: IconProps & { children: ReactNode }) {
  return (
    <svg className="stroke-icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round"
      {...(label ? { role: 'img', 'aria-label': label } : { 'aria-hidden': true })}>
      {children}
    </svg>
  )
}

export function GlobeIcon(props: IconProps) {
  return <StrokeIcon {...props}><circle cx="12" cy="12" r="9" /><path d="M3 12h18" /><path d="M12 3c2.8 3 2.8 15 0 18c-2.8-3-2.8-15 0-18z" /></StrokeIcon>
}

export function LockIcon(props: IconProps) {
  return <StrokeIcon strokeWidth={2} {...props}><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" /></StrokeIcon>
}

export function EyeIcon(props: IconProps) {
  return <StrokeIcon strokeWidth={1.8} {...props}><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z" /><circle cx="12" cy="12" r="3" /></StrokeIcon>
}

export function TrashIcon(props: IconProps) {
  return <StrokeIcon strokeWidth={1.8} {...props}><path d="M4 7h16" /><path d="M9 7V4h6v3" /><path d="M6 7l1 13h10l1-13" /></StrokeIcon>
}

export function PinIcon(props: IconProps) {
  return <StrokeIcon {...props}><path d="M12 21s-6-5.7-6-11a6 6 0 0 1 12 0c0 5.3-6 11-6 11z" /><circle cx="12" cy="10" r="2.2" /></StrokeIcon>
}

export function PersonIcon(props: IconProps) {
  return <StrokeIcon {...props}><circle cx="12" cy="8" r="3.5" /><path d="M5 20c1.2-3.6 3.8-5.5 7-5.5s5.8 1.9 7 5.5" /></StrokeIcon>
}

export function BannerIcon(props: IconProps) {
  return <StrokeIcon {...props}><path d="M6 21V4" /><path d="M6 4h11l-2.5 4 2.5 4H6" /></StrokeIcon>
}

export function GemIcon(props: IconProps) {
  return <StrokeIcon {...props}><path d="M12 3l7 9-7 9-7-9z" /></StrokeIcon>
}

export function HourglassIcon(props: IconProps) {
  return <StrokeIcon {...props}><path d="M7 3h10" /><path d="M7 21h10" /><path d="M8 3c0 6 8 6 8 9s-8 3-8 9" /><path d="M16 3c0 6-8 6-8 9s8 3 8 9" /></StrokeIcon>
}
