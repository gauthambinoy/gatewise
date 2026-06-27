import { useEffect, useState } from 'react'
import type { CSSProperties, ReactNode } from 'react'
import { useT } from '../../lib/i18n'

// Surface & display primitives for the component library. Imported via the barrel `../ui`.
// Pure React 18 + TS — no MUI. Re-uses the `.card`, `.badge`, `.stat`, `.avatar` classes from
// tokens.css and only ever references the design-token CSS variables (never hardcoded colours).

// Locally inlined (not imported/exported) to avoid a circular import with the barrel.
type Tone = 'info' | 'danger' | 'success' | 'warning'

/** True when the user has asked the OS to reduce motion. SSR-safe. */
function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  )
}

/** A surface panel — wraps the shared `.card` class. `interactive` makes it behave like a button
 * (pointer cursor + a stronger lift on hover). */
export function Card({
  children,
  padding,
  interactive,
  className,
  style,
  onClick,
}: {
  children: ReactNode
  padding?: number | string
  interactive?: boolean
  className?: string
  style?: CSSProperties
  onClick?: () => void
}) {
  const [hover, setHover] = useState(false)
  const reduce = prefersReducedMotion()
  const lift = interactive && hover && !reduce

  return (
    <div
      className={`card${className ? ` ${className}` : ''}`}
      onClick={onClick}
      onMouseEnter={interactive ? () => setHover(true) : undefined}
      onMouseLeave={interactive ? () => setHover(false) : undefined}
      style={{
        ...(padding !== undefined ? { padding } : null),
        ...(interactive
          ? {
              cursor: 'pointer',
              transition: reduce ? undefined : 'transform 0.2s var(--ease-out), box-shadow 0.25s var(--ease-out)',
              transform: lift ? 'translateY(-2px)' : undefined,
              boxShadow: lift ? 'var(--shadow-lg)' : undefined,
            }
          : null),
        ...style,
      }}
    >
      {children}
    </div>
  )
}

/** A card's heading row: optional leading icon, a title with optional subtitle below, and
 * right-aligned actions. Adds a bottom margin to separate it from the card body. */
export function CardHeader({
  title,
  subtitle,
  icon,
  actions,
}: {
  title: ReactNode
  subtitle?: ReactNode
  icon?: string
  actions?: ReactNode
}) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: subtitle ? 'flex-start' : 'center',
        justifyContent: 'space-between',
        gap: 'var(--sp-3)',
        marginBottom: 'var(--sp-4)',
      }}
    >
      <div style={{ display: 'flex', alignItems: subtitle ? 'flex-start' : 'center', gap: 'var(--sp-3)', minWidth: 0 }}>
        {icon && (
          <div
            style={{
              width: 34,
              height: 34,
              flexShrink: 0,
              borderRadius: 'var(--border-radius-md)',
              background: 'var(--color-background-secondary)',
              color: 'var(--color-text-secondary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <i className={`ti ${icon}`} style={{ fontSize: 18 }} aria-hidden />
          </div>
        )}
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: 15, fontWeight: 600 }}>{title}</div>
          {subtitle && (
            <div className="muted" style={{ fontSize: 12, marginTop: 2 }}>
              {subtitle}
            </div>
          )}
        </div>
      </div>
      {actions && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-2)', flexShrink: 0 }}>{actions}</div>
      )}
    </div>
  )
}

/** A richer metric tile than {@link Stat}: optional icon chip, a label, a large (optionally
 * tone-coloured) value, an optional hint line and a coloured up/down trend. */
export function StatCard({
  label,
  value,
  icon,
  tone,
  hint,
  trend,
}: {
  label: string
  value: ReactNode
  icon?: string
  tone?: Tone
  hint?: ReactNode
  trend?: { dir: 'up' | 'down'; value: string }
}) {
  // "Up" is positive (success), "down" is negative (danger) — independent of any value tone.
  const trendColor = trend ? `var(--color-text-${trend.dir === 'up' ? 'success' : 'danger'})` : undefined

  return (
    <div className="card" style={{ padding: '16px 18px' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 'var(--sp-2)',
          marginBottom: 'var(--sp-2)',
        }}
      >
        <span style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>{label}</span>
        {icon && (
          <span
            style={{
              width: 30,
              height: 30,
              flexShrink: 0,
              borderRadius: 'var(--border-radius-md)',
              background: tone ? `var(--color-background-${tone})` : 'var(--color-background-secondary)',
              color: tone ? `var(--color-text-${tone})` : 'var(--color-text-secondary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <i className={`ti ${icon}`} style={{ fontSize: 16 }} aria-hidden />
          </span>
        )}
      </div>
      <div
        style={{
          fontSize: 30,
          fontWeight: 700,
          lineHeight: 1.15,
          letterSpacing: '-0.03em',
          color: tone ? `var(--color-text-${tone})` : undefined,
          ...(tone
            ? {}
            : {
                background: 'var(--accent-grad-soft)',
                WebkitBackgroundClip: 'text',
                backgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
              }),
        }}
      >
        {value}
      </div>
      {(hint || trend) && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 'var(--sp-2)',
            marginTop: 'var(--sp-2)',
            fontSize: 12,
          }}
        >
          {trend && (
            <span
              style={{ display: 'inline-flex', alignItems: 'center', gap: 3, color: trendColor, fontWeight: 500 }}
            >
              <i
                className={`ti ti-trending-${trend.dir}`}
                style={{ fontSize: 14 }}
                aria-hidden
              />
              {trend.value}
            </span>
          )}
          {hint && <span style={{ color: 'var(--color-text-tertiary)' }}>{hint}</span>}
        </div>
      )}
    </div>
  )
}

const ALERT_ICONS: Record<Tone, string> = {
  info: 'ti-info-circle',
  success: 'ti-circle-check',
  warning: 'ti-alert-triangle',
  danger: 'ti-alert-octagon',
}

/** A banner with a tinted, tone-matched background, a leading icon and an optional close button. */
export function Alert({
  tone,
  title,
  children,
  icon,
  onClose,
}: {
  tone: Tone
  title?: ReactNode
  children?: ReactNode
  icon?: string
  onClose?: () => void
}) {
  const { t } = useT()
  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 'var(--sp-3)',
        padding: '12px 14px',
        borderRadius: 'var(--border-radius-md)',
        background: `var(--color-background-${tone})`,
        color: `var(--color-text-${tone})`,
        fontSize: 13,
      }}
    >
      <i
        className={`ti ${icon ?? ALERT_ICONS[tone]}`}
        style={{ fontSize: 18, lineHeight: 1.4, flexShrink: 0 }}
        aria-hidden
      />
      <div style={{ flex: 1, minWidth: 0 }}>
        {title && <div style={{ fontWeight: 600, marginBottom: children ? 2 : 0 }}>{title}</div>}
        {children && <div style={{ opacity: title ? 0.92 : 1 }}>{children}</div>}
      </div>
      {onClose && (
        <button
          type="button"
          onClick={onClose}
          aria-label={t('common.dismiss')}
          style={{
            flexShrink: 0,
            border: 'none',
            background: 'transparent',
            color: 'inherit',
            padding: 2,
            margin: '-2px -4px -2px 0',
            minHeight: 0,
            lineHeight: 1,
            opacity: 0.8,
          }}
        >
          <i className="ti ti-x" style={{ fontSize: 16 }} aria-hidden />
        </button>
      )}
    </div>
  )
}

/** A pill — distinct from {@link Badge}: rounded, with an optional leading icon and a removable
 * trailing `x`. Pops in on mount (motion-aware). */
export function Chip({
  children,
  tone,
  icon,
  onRemove,
  size = 'md',
}: {
  children: ReactNode
  tone?: Tone
  icon?: string
  onRemove?: () => void
  size?: 'sm' | 'md'
}) {
  const { t } = useT()
  const reduce = prefersReducedMotion()
  const [shown, setShown] = useState(reduce)
  useEffect(() => setShown(true), [])

  const sm = size === 'sm'

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: sm ? 4 : 6,
        padding: sm ? '2px 8px' : '4px 10px',
        fontSize: sm ? 11 : 12,
        fontWeight: 500,
        borderRadius: 999,
        background: tone ? `var(--color-background-${tone})` : 'var(--color-background-secondary)',
        color: tone ? `var(--color-text-${tone})` : 'var(--color-text-secondary)',
        transition: reduce ? undefined : 'transform 0.2s var(--ease-spring), opacity 0.2s var(--ease-out)',
        transform: shown ? 'scale(1)' : 'scale(0.8)',
        opacity: shown ? 1 : 0,
      }}
    >
      {icon && <i className={`ti ${icon}`} style={{ fontSize: sm ? 12 : 14 }} aria-hidden />}
      {children}
      {onRemove && (
        <button
          type="button"
          onClick={onRemove}
          aria-label={t('common.remove')}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            border: 'none',
            background: 'transparent',
            color: 'inherit',
            padding: 0,
            margin: sm ? '0 -2px 0 0' : '0 -3px 0 1px',
            minHeight: 0,
            lineHeight: 1,
            opacity: 0.7,
          }}
        >
          <i className="ti ti-x" style={{ fontSize: sm ? 12 : 14 }} aria-hidden />
        </button>
      )}
    </span>
  )
}

/** Derives up to two uppercase initials from a person/entity name. */
function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return ''
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

/** A circular avatar — re-uses the `.avatar` look. Shows {@link src} as an image, otherwise the
 * initials of {@link name}, otherwise a fallback user icon. */
export function Avatar({ name, src, size = 30 }: { name?: string; src?: string; size?: number }) {
  const { t } = useT()
  const label = name ? initials(name) : ''

  return (
    <span
      className="avatar"
      title={name}
      role="img"
      aria-label={name ?? t('common.avatar')}
      style={{
        width: size,
        height: size,
        fontSize: Math.max(10, Math.round(size * 0.4)),
        overflow: 'hidden',
        flexShrink: 0,
      }}
    >
      {src ? (
        <img
          src={src}
          alt={name ?? ''}
          style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
        />
      ) : label ? (
        label
      ) : (
        <i className="ti ti-user" style={{ fontSize: Math.max(12, Math.round(size * 0.5)) }} aria-hidden />
      )}
    </span>
  )
}

/** A horizontal progress meter. Animates its fill on mount (scaleX from the left). `gradient`
 * paints the fill with the brand gradient instead of a flat tone colour. */
export function ProgressBar({
  value,
  tone,
  height = 8,
  gradient,
}: {
  value: number
  tone?: Tone
  height?: number
  gradient?: boolean
}) {
  const reduce = prefersReducedMotion()
  const pct = Math.max(0, Math.min(100, value))
  const [grown, setGrown] = useState(reduce)
  useEffect(() => setGrown(true), [])

  const fill = gradient
    ? 'linear-gradient(90deg, var(--color-text-info), #7c5ce8)'
    : tone
      ? `var(--color-text-${tone})`
      : 'var(--primary)'

  return (
    <div
      role="progressbar"
      aria-valuenow={Math.round(pct)}
      aria-valuemin={0}
      aria-valuemax={100}
      style={{
        width: '100%',
        height,
        borderRadius: 999,
        background: 'var(--color-background-secondary)',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          width: `${pct}%`,
          height: '100%',
          borderRadius: 'inherit',
          background: fill,
          transformOrigin: 'left center',
          transform: `scaleX(${grown ? 1 : 0})`,
          transition: reduce ? undefined : 'transform 0.9s var(--ease-out)',
        }}
      />
    </div>
  )
}

/** A thin rule. With a {@link label}, the text is centred with a rule on each side. `vertical`
 * draws a self-stretching vertical line instead (labels are ignored when vertical). */
export function Divider({ label, vertical }: { label?: ReactNode; vertical?: boolean }) {
  if (vertical) {
    return (
      <div
        role="separator"
        aria-orientation="vertical"
        style={{
          alignSelf: 'stretch',
          width: 1,
          minHeight: 16,
          background: 'var(--color-border-tertiary)',
          flexShrink: 0,
        }}
      />
    )
  }

  if (label) {
    return (
      <div
        role="separator"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--sp-3)',
          margin: 'var(--sp-3) 0',
          fontSize: 12,
          color: 'var(--color-text-tertiary)',
        }}
      >
        <span style={{ flex: 1, height: 1, background: 'var(--color-border-tertiary)' }} />
        <span style={{ whiteSpace: 'nowrap' }}>{label}</span>
        <span style={{ flex: 1, height: 1, background: 'var(--color-border-tertiary)' }} />
      </div>
    )
  }

  return (
    <hr
      style={{
        border: 'none',
        height: 1,
        background: 'var(--color-border-tertiary)',
        margin: 'var(--sp-3) 0',
      }}
    />
  )
}
