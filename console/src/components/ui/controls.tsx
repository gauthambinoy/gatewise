import { useId, useState } from 'react'
import type { CSSProperties, ReactNode } from 'react'
import { useT } from '../../lib/i18n'

// Form-control primitives for the Auvex console. Pure React 18 + TypeScript — no MUI or any
// external UI library. Styling is inline + the shared className tokens from tokens.css /
// animations.css; colours come exclusively from CSS variables so light/dark themes just work.
//
// Note: none of these controls take a `tone` prop, so the shared Tone union is not needed here.
// If one is added later, inline it locally (`type Tone = 'info' | 'danger' | 'success' |
// 'warning'`) rather than importing it from ../ui, to avoid a circular import.

const prefersReducedMotion = () =>
  typeof window !== 'undefined' &&
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true

/* ------------------------------------------------------------------ Button */

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'

const BUTTON_SIZES = {
  sm: { padding: '6px 12px', fontSize: 12, gap: 6, iconSize: 15 },
  md: { padding: '9px 16px', fontSize: 13, gap: 8, iconSize: 17 },
} as const

/** The primary call-to-action. Variants map to the design system's button looks; `loading` swaps
 * the leading icon for a spinner and disables interaction. */
export function Button({
  variant = 'secondary',
  size = 'md',
  loading = false,
  icon,
  iconRight,
  fullWidth = false,
  disabled = false,
  type = 'button',
  onClick,
  children,
  style,
}: {
  variant?: ButtonVariant
  size?: 'sm' | 'md'
  loading?: boolean
  icon?: string
  iconRight?: string
  fullWidth?: boolean
  disabled?: boolean
  type?: 'button' | 'submit'
  onClick?: () => void
  children?: ReactNode
  style?: CSSProperties
}) {
  const [hover, setHover] = useState(false)
  const sz = BUTTON_SIZES[size]
  const isDisabled = disabled || loading
  const reduce = prefersReducedMotion()
  const lift = hover && !isDisabled && !reduce

  const palette: Record<ButtonVariant, CSSProperties> = {
    primary: {
      background: 'var(--accent-grad)',
      color: '#fff',
      border: 'none',
      boxShadow: lift
        ? '0 10px 30px -6px rgba(109, 92, 255, 0.6)'
        : '0 4px 16px -4px rgba(109, 92, 255, 0.45)',
    },
    secondary: {
      background: hover && !isDisabled ? 'var(--color-background-secondary)' : 'transparent',
      color: 'var(--color-text-primary)',
      border: '0.5px solid var(--color-border-secondary)',
    },
    ghost: {
      background: hover && !isDisabled ? 'var(--color-background-secondary)' : 'transparent',
      color: 'var(--color-text-secondary)',
      border: '0.5px solid transparent',
    },
    danger: {
      background: hover && !isDisabled ? 'var(--color-background-danger)' : 'transparent',
      color: 'var(--color-text-danger)',
      border: '0.5px solid var(--color-border-danger)',
    },
  }

  return (
    <button
      type={type}
      onClick={onClick}
      disabled={isDisabled}
      aria-busy={loading || undefined}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: sz.gap,
        width: fullWidth ? '100%' : undefined,
        padding: sz.padding,
        fontSize: sz.fontSize,
        fontWeight: 500,
        lineHeight: 1.2,
        borderRadius: 'var(--border-radius-md)',
        cursor: isDisabled ? 'not-allowed' : 'pointer',
        opacity: isDisabled ? 0.55 : 1,
        transform: lift ? 'translateY(-1px)' : 'translateY(0)',
        transition: reduce
          ? 'none'
          : 'transform 0.2s var(--ease-spring), box-shadow 0.3s var(--ease-out), background-color 0.25s var(--ease-out), filter 0.2s',
        filter: lift && variant === 'primary' ? 'brightness(1.08)' : undefined,
        ...palette[variant],
        ...style,
      }}
    >
      {loading ? (
        <i className="ti ti-loader-2 spin" aria-hidden style={{ fontSize: sz.iconSize }} />
      ) : (
        icon && <i className={`ti ${icon}`} aria-hidden style={{ fontSize: sz.iconSize }} />
      )}
      {children}
      {iconRight && !loading && (
        <i className={`ti ${iconRight}`} aria-hidden style={{ fontSize: sz.iconSize }} />
      )}
    </button>
  )
}

/* -------------------------------------------------------------- IconButton */

const ICON_BUTTON_SIZES = {
  sm: { box: 30, iconSize: 16 },
  md: { box: 36, iconSize: 18 },
} as const

/** A square, icon-only button. Always supply a `label` for screen readers. */
export function IconButton({
  icon,
  label,
  onClick,
  size = 'md',
  variant = 'ghost',
  disabled = false,
}: {
  icon: string
  label: string
  onClick?: () => void
  size?: 'sm' | 'md'
  variant?: 'ghost' | 'solid'
  disabled?: boolean
}) {
  const [hover, setHover] = useState(false)
  const sz = ICON_BUTTON_SIZES[size]
  const reduce = prefersReducedMotion()
  const active = hover && !disabled

  const solid = variant === 'solid'

  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={onClick}
      disabled={disabled}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: sz.box,
        height: sz.box,
        minHeight: 0,
        padding: 0,
        borderRadius: 'var(--border-radius-md)',
        border: solid ? 'none' : '0.5px solid var(--color-border-secondary)',
        background: solid
          ? 'var(--color-background-secondary)'
          : active
            ? 'var(--color-background-secondary)'
            : 'transparent',
        color: 'var(--color-text-secondary)',
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.55 : 1,
        transform: active && !reduce ? 'scale(1.06)' : 'scale(1)',
        transition: reduce
          ? 'none'
          : 'transform 0.2s var(--ease-spring), background-color 0.25s var(--ease-out)',
      }}
    >
      <i className={`ti ${icon}`} aria-hidden style={{ fontSize: sz.iconSize }} />
    </button>
  )
}

/* --------------------------------------------------------------- TextField */

const FIELD_SIZES = {
  sm: { minHeight: 32, fontSize: 12, padX: 10 },
  md: { minHeight: 36, fontSize: 13, padX: 12 },
} as const

/** A labelled text input with an optional leading icon and helper/error text. */
export function TextField({
  label,
  value,
  onChange,
  placeholder,
  type = 'text',
  icon,
  error,
  helper,
  fullWidth = false,
  size = 'md',
}: {
  label?: string
  value: string
  onChange: (v: string) => void
  placeholder?: string
  type?: string
  icon?: string
  error?: string
  helper?: string
  fullWidth?: boolean
  size?: 'sm' | 'md'
}) {
  const id = useId()
  const describedById = `${id}-desc`
  const sz = FIELD_SIZES[size]
  const [focus, setFocus] = useState(false)
  const reduce = prefersReducedMotion()

  const borderColor = error
    ? 'var(--color-border-danger)'
    : focus
      ? 'var(--color-border-info)'
      : 'var(--color-border-secondary)'

  return (
    <div style={{ width: fullWidth ? '100%' : undefined, display: 'inline-block' }}>
      {label && (
        <label
          htmlFor={id}
          style={{
            display: 'block',
            fontSize: 12,
            color: 'var(--color-text-secondary)',
            marginBottom: 4,
          }}
        >
          {label}
        </label>
      )}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          width: fullWidth ? '100%' : undefined,
          minHeight: sz.minHeight,
          padding: `0 ${sz.padX}px`,
          gap: 8,
          background: 'var(--color-background-primary)',
          border: `0.5px solid ${borderColor}`,
          borderRadius: 'var(--border-radius-md)',
          boxShadow: focus
            ? `0 0 0 3px ${error ? 'var(--color-background-danger)' : 'var(--color-background-info)'}`
            : 'none',
          transition: reduce ? 'none' : 'border-color 0.2s ease, box-shadow 0.2s ease',
        }}
      >
        {icon && (
          <i
            className={`ti ${icon}`}
            aria-hidden
            style={{ fontSize: 16, color: 'var(--color-text-tertiary)', flexShrink: 0 }}
          />
        )}
        <input
          id={id}
          type={type}
          value={value}
          placeholder={placeholder}
          onChange={(e) => onChange(e.target.value)}
          onFocus={() => setFocus(true)}
          onBlur={() => setFocus(false)}
          aria-invalid={error ? true : undefined}
          aria-describedby={error || helper ? describedById : undefined}
          style={{
            flex: 1,
            minWidth: 0,
            minHeight: 0,
            padding: '7px 0',
            border: 'none',
            background: 'transparent',
            color: 'var(--color-text-primary)',
            fontSize: sz.fontSize,
            outline: 'none',
            boxShadow: 'none',
          }}
        />
      </div>
      {(error || helper) && (
        <div
          id={describedById}
          style={{
            marginTop: 4,
            fontSize: 11,
            color: error ? 'var(--color-text-danger)' : 'var(--color-text-tertiary)',
          }}
        >
          {error || helper}
        </div>
      )}
    </div>
  )
}

/* ------------------------------------------------------------------ Select */

/** A styled wrapper around the native `<select>` with a chevron and focus ring. */
export function Select({
  label,
  value,
  onChange,
  options,
  fullWidth = false,
}: {
  label?: string
  value: string
  onChange: (v: string) => void
  options: { value: string; label: string }[]
  fullWidth?: boolean
}) {
  const id = useId()
  const [focus, setFocus] = useState(false)
  const reduce = prefersReducedMotion()

  return (
    <div style={{ width: fullWidth ? '100%' : undefined, display: 'inline-block' }}>
      {label && (
        <label
          htmlFor={id}
          style={{
            display: 'block',
            fontSize: 12,
            color: 'var(--color-text-secondary)',
            marginBottom: 4,
          }}
        >
          {label}
        </label>
      )}
      <div style={{ position: 'relative', width: fullWidth ? '100%' : undefined }}>
        <select
          id={id}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onFocus={() => setFocus(true)}
          onBlur={() => setFocus(false)}
          style={{
            width: '100%',
            minHeight: 36,
            padding: '7px 34px 7px 12px',
            border: `0.5px solid ${focus ? 'var(--color-border-info)' : 'var(--color-border-secondary)'}`,
            borderRadius: 'var(--border-radius-md)',
            background: 'var(--color-background-primary)',
            color: 'var(--color-text-primary)',
            fontSize: 13,
            outline: 'none',
            boxShadow: focus ? '0 0 0 3px var(--color-background-info)' : 'none',
            appearance: 'none',
            WebkitAppearance: 'none',
            MozAppearance: 'none',
            cursor: 'pointer',
            transition: reduce ? 'none' : 'border-color 0.2s ease, box-shadow 0.2s ease',
          }}
        >
          {options.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        <i
          className="ti ti-chevron-down"
          aria-hidden
          style={{
            position: 'absolute',
            right: 11,
            top: '50%',
            transform: 'translateY(-50%)',
            fontSize: 16,
            color: 'var(--color-text-tertiary)',
            pointerEvents: 'none',
          }}
        />
      </div>
    </div>
  )
}

/* ------------------------------------------------------------- SearchInput */

/** A search box with a leading magnifier, a clear button when filled, and Enter-to-submit. */
export function SearchInput({
  value,
  onChange,
  onSubmit,
  placeholder,
}: {
  value: string
  onChange: (v: string) => void
  onSubmit?: () => void
  placeholder?: string
}) {
  const { t } = useT()
  const [focus, setFocus] = useState(false)
  const reduce = prefersReducedMotion()

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        minHeight: 36,
        padding: '0 10px',
        background: 'var(--color-background-primary)',
        border: `0.5px solid ${focus ? 'var(--color-border-info)' : 'var(--color-border-secondary)'}`,
        borderRadius: 'var(--border-radius-md)',
        boxShadow: focus ? '0 0 0 3px var(--color-background-info)' : 'none',
        transition: reduce ? 'none' : 'border-color 0.2s ease, box-shadow 0.2s ease',
      }}
    >
      <i
        className="ti ti-search"
        aria-hidden
        style={{ fontSize: 16, color: 'var(--color-text-tertiary)', flexShrink: 0 }}
      />
      <input
        type="search"
        role="searchbox"
        value={value}
        placeholder={placeholder ?? t('common.searchPlaceholder')}
        onChange={(e) => onChange(e.target.value)}
        onFocus={() => setFocus(true)}
        onBlur={() => setFocus(false)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') onSubmit?.()
        }}
        style={{
          flex: 1,
          minWidth: 0,
          minHeight: 0,
          padding: '7px 0',
          border: 'none',
          background: 'transparent',
          color: 'var(--color-text-primary)',
          fontSize: 13,
          outline: 'none',
          boxShadow: 'none',
        }}
      />
      {value && (
        <button
          type="button"
          aria-label={t('common.clearSearch')}
          title={t('common.clearSearch')}
          onClick={() => onChange('')}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 22,
            height: 22,
            minHeight: 0,
            padding: 0,
            border: 'none',
            borderRadius: 'var(--border-radius-md)',
            background: 'transparent',
            color: 'var(--color-text-tertiary)',
            cursor: 'pointer',
            flexShrink: 0,
          }}
        >
          <i className="ti ti-x" aria-hidden style={{ fontSize: 15 }} />
        </button>
      )}
    </div>
  )
}

/* ------------------------------------------------------------------ Switch */

/** An accessible on/off toggle with an animated knob, primary-coloured when on. */
export function Switch({
  checked,
  onChange,
  label,
  disabled = false,
}: {
  checked: boolean
  onChange: (v: boolean) => void
  label?: string
  disabled?: boolean
}) {
  const { t } = useT()
  const reduce = prefersReducedMotion()
  const W = 40
  const H = 22
  const KNOB = 16

  const toggle = () => {
    if (!disabled) onChange(!checked)
  }

  const control = (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label ? undefined : t('common.toggle')}
      disabled={disabled}
      onClick={toggle}
      style={{
        position: 'relative',
        flexShrink: 0,
        width: W,
        height: H,
        minHeight: 0,
        padding: 0,
        border: 'none',
        borderRadius: H,
        background: checked ? 'var(--primary)' : 'var(--color-border-secondary)',
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.55 : 1,
        transition: reduce ? 'none' : 'background-color 0.25s var(--ease-out)',
      }}
    >
      <span
        aria-hidden
        style={{
          position: 'absolute',
          top: (H - KNOB) / 2,
          left: checked ? W - KNOB - (H - KNOB) / 2 : (H - KNOB) / 2,
          width: KNOB,
          height: KNOB,
          borderRadius: '50%',
          background: '#fff',
          boxShadow: 'var(--shadow-sm)',
          transition: reduce ? 'none' : 'left 0.25s var(--ease-spring)',
        }}
      />
    </button>
  )

  if (!label) return control

  return (
    <label
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 10,
        margin: 0,
        fontSize: 13,
        color: 'var(--color-text-primary)',
        cursor: disabled ? 'not-allowed' : 'pointer',
      }}
    >
      {control}
      <span>{label}</span>
    </label>
  )
}

/* ---------------------------------------------------------------- Checkbox */

/** A custom-styled checkbox with a Tabler check glyph. */
export function Checkbox({
  checked,
  onChange,
  label,
}: {
  checked: boolean
  onChange: (v: boolean) => void
  label?: string
}) {
  const reduce = prefersReducedMotion()

  const box = (
    <span
      aria-hidden
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0,
        width: 18,
        height: 18,
        borderRadius: 'var(--border-radius-md)',
        border: `0.5px solid ${checked ? 'var(--primary)' : 'var(--color-border-secondary)'}`,
        background: checked ? 'var(--primary)' : 'var(--color-background-primary)',
        color: '#fff',
        transition: reduce ? 'none' : 'background-color 0.18s var(--ease-out), border-color 0.18s var(--ease-out)',
      }}
    >
      <i
        className="ti ti-check"
        style={{
          fontSize: 13,
          opacity: checked ? 1 : 0,
          transform: checked || reduce ? 'scale(1)' : 'scale(0.6)',
          transition: reduce ? 'none' : 'opacity 0.15s ease, transform 0.18s var(--ease-spring)',
        }}
      />
    </span>
  )

  return (
    <label
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 10,
        margin: 0,
        fontSize: 13,
        color: 'var(--color-text-primary)',
        cursor: 'pointer',
      }}
    >
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        style={{
          position: 'absolute',
          width: 1,
          height: 1,
          padding: 0,
          margin: -1,
          minHeight: 0,
          overflow: 'hidden',
          clip: 'rect(0 0 0 0)',
          whiteSpace: 'nowrap',
          border: 0,
        }}
      />
      {box}
      {label && <span>{label}</span>}
    </label>
  )
}
