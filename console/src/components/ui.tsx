import type { CSSProperties, ReactNode } from 'react'

export type Tone = 'info' | 'danger' | 'success' | 'warning'

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

/** Formats a number as USD. */
export function money(n: number | null | undefined): string {
  if (n === null || n === undefined) return '—'
  return n.toLocaleString('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 4 })
}

/** Formats an ISO timestamp as a short local time. */
export function dt(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
}

/** Formats an ISO timestamp as HH:MM:SS. */
export function clock(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleTimeString()
}
