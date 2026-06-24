import { useEffect, useRef, useState } from 'react'
import type { CSSProperties, ReactNode } from 'react'
import { intlLocale } from '../lib/i18n'

// The component library (MUI-grade primitives) lives in sibling files; re-export so every page can
// import from one place: `../components/ui`.
export * from './ui/controls'
export * from './ui/surfaces'
export * from './ui/data'
export * from './ui/overlays'

export type Tone = 'info' | 'danger' | 'success' | 'warning'

/** Animates a number from 0 to {@link end} on mount (eased), optionally formatted. Honours
 * prefers-reduced-motion by snapping straight to the final value. */
export function CountUp({
  end,
  format,
  duration = 950,
}: {
  end: number
  format?: (n: number) => string
  duration?: number
}) {
  const reduce =
    typeof window !== 'undefined' &&
    window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  const [n, setN] = useState(reduce ? end : 0)
  const raf = useRef<number>()

  useEffect(() => {
    if (reduce) {
      setN(end)
      return
    }
    const start = performance.now()
    const tick = (t: number) => {
      const p = Math.min(1, (t - start) / duration)
      setN(end * (1 - Math.pow(1 - p, 3))) // easeOutCubic
      if (p < 1) raf.current = requestAnimationFrame(tick)
    }
    raf.current = requestAnimationFrame(tick)
    return () => {
      if (raf.current) cancelAnimationFrame(raf.current)
    }
  }, [end, duration, reduce])

  return <>{format ? format(n) : Math.round(n).toLocaleString()}</>
}

/** A page title + optional subtitle and right-aligned actions. */
export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string
  subtitle?: string
  actions?: ReactNode
}) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: subtitle ? 2 : 16,
      }}
    >
      <div>
        <div className="page-title">{title}</div>
        {subtitle && (
          <div className="muted" style={{ fontSize: 12 }}>
            {subtitle}
          </div>
        )}
      </div>
      {actions && <div style={{ display: 'flex', gap: 8 }}>{actions}</div>}
    </div>
  )
}

/** A metric tile. */
export function Stat({ label, value, tone }: { label: string; value: ReactNode; tone?: Tone }) {
  return (
    <div className="stat">
      <div className="label">{label}</div>
      <div className="value" style={tone ? { color: `var(--color-text-${tone})` } : undefined}>
        {value}
      </div>
    </div>
  )
}

export function Badge({ children, tone }: { children: ReactNode; tone?: Tone }) {
  return <span className={`badge${tone ? ` badge-${tone}` : ''}`}>{children}</span>
}

/** Maps an audit verdict to a badge tone. */
export function verdictTone(verdict: string): Tone {
  if (verdict === 'blocked') return 'danger'
  if (verdict === 'redacted') return 'info'
  return 'success'
}

export function Spinner() {
  return <i className="ti ti-loader-2 spin" aria-hidden />
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="card" style={{ textAlign: 'center', color: 'var(--color-text-tertiary)' }}>
      <Spinner /> {label}
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div
      className="card"
      style={{
        borderColor: 'var(--color-border-danger)',
        background: 'var(--color-background-danger)',
        textAlign: 'center',
      }}
    >
      <i
        className="ti ti-alert-triangle"
        style={{ fontSize: 24, color: 'var(--color-text-danger)' }}
      />
      <div style={{ fontWeight: 500, margin: '8px 0 4px' }}>Something went wrong</div>
      <div className="sub" style={{ fontSize: 13, marginBottom: onRetry ? 14 : 0 }}>
        {message}
      </div>
      {onRetry && (
        <button onClick={onRetry} style={{ padding: '8px 16px', fontSize: 13 }}>
          <i className="ti ti-refresh" /> Retry
        </button>
      )}
    </div>
  )
}

export function EmptyState({
  icon = 'ti-inbox',
  title,
  message,
  action,
}: {
  icon?: string
  title: string
  message?: string
  action?: ReactNode
}) {
  return (
    <div className="card empty">
      <div
        style={{
          width: 48,
          height: 48,
          borderRadius: 12,
          background: 'var(--color-background-secondary)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          margin: '0 auto 14px',
        }}
      >
        <i className={`ti ${icon}`} style={{ fontSize: 24, color: 'var(--color-text-secondary)' }} />
      </div>
      <div style={{ fontWeight: 500, marginBottom: 6 }}>{title}</div>
      {message && (
        <div className="sub" style={{ fontSize: 13, maxWidth: 340, margin: '0 auto 16px' }}>
          {message}
        </div>
      )}
      {action}
    </div>
  )
}

/** A skeleton block for loading states. */
export function Skeleton({ style }: { style?: CSSProperties }) {
  return <div className="skel" style={{ height: 14, ...style }} />
}

/** Formats a USD amount in the active European locale's conventions (e.g. de-DE → "1.234,56 $"). */
export function money(n: number | null | undefined): string {
  if (n === null || n === undefined) return '—'
  return n.toLocaleString(intlLocale(), {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 4,
  })
}

/** Formats a plain number in the active locale (European grouping/decimals). */
export function num(n: number | null | undefined): string {
  if (n === null || n === undefined) return '—'
  return n.toLocaleString(intlLocale())
}

/** Formats an ISO timestamp as a short local date-time, in the active locale. */
export function dt(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString(intlLocale())
}

/** Formats an ISO timestamp as a locale time. */
export function clock(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleTimeString(intlLocale())
}
