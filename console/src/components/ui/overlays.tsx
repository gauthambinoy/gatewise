import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useId,
  useRef,
  useState,
} from 'react'
import { createPortal } from 'react-dom'
import type { CSSProperties, KeyboardEvent as ReactKeyboardEvent, ReactNode } from 'react'
import { useT } from '../../lib/i18n'

// Layered overlay primitives (Dialog, Tooltip, Menu, Toast) — built on plain React + portals, no
// external UI lib. Everything draws from the design tokens in styles/tokens.css + animations.css;
// colours are referenced exclusively through CSS variables. Motion respects prefers-reduced-motion.

type Tone = 'info' | 'danger' | 'success' | 'warning'

/** True when the user has asked the OS to minimise non-essential motion. */
function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  )
}

/** Maps a tone to its semantic icon + text colour variable. */
const TONE_ICON: Record<Tone, string> = {
  info: 'ti-info-circle',
  danger: 'ti-alert-triangle',
  success: 'ti-circle-check',
  warning: 'ti-alert-circle',
}

// ───────────────────────────── Dialog ─────────────────────────────

const DIALOG_WIDTHS: Record<'sm' | 'md' | 'lg', number> = { sm: 380, md: 520, lg: 720 }

/** A modal dialog, portalled to `document.body`: dimmed backdrop (click closes) + a centred card
 * surface that fades/scales in. Closes on ESC, locks body scroll, traps initial focus on the panel
 * and restores it on close. `role="dialog"` + `aria-modal`. */
export function Dialog({
  open,
  onClose,
  title,
  children,
  actions,
  size = 'md',
}: {
  open: boolean
  onClose: () => void
  title?: ReactNode
  children: ReactNode
  actions?: ReactNode
  size?: 'sm' | 'md' | 'lg'
}) {
  const { t } = useT()
  const panelRef = useRef<HTMLDivElement>(null)
  const labelId = useId()
  const reduce = prefersReducedMotion()

  // ESC to close + body scroll lock + focus management, all gated on `open`.
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation()
        onClose()
      }
    }
    document.addEventListener('keydown', onKey)

    const prevOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const prevFocus = document.activeElement as HTMLElement | null
    // Defer so the portal node is mounted before we move focus into it.
    const id = window.setTimeout(() => panelRef.current?.focus(), 0)

    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = prevOverflow
      window.clearTimeout(id)
      prevFocus?.focus?.()
    }
  }, [open, onClose])

  if (!open) return null

  const backdrop: CSSProperties = {
    position: 'fixed',
    inset: 0,
    zIndex: 1000,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 'var(--sp-4)',
    background: 'rgba(0, 0, 0, 0.5)',
    backdropFilter: 'blur(3px)',
    WebkitBackdropFilter: 'blur(3px)',
    animation: reduce ? undefined : 'gatewiseFade 0.2s var(--ease-out)',
  }

  const panel: CSSProperties = {
    position: 'relative',
    width: '100%',
    maxWidth: DIALOG_WIDTHS[size],
    maxHeight: 'calc(100vh - var(--sp-8))',
    display: 'flex',
    flexDirection: 'column',
    background: 'var(--color-background-primary)',
    border: '0.5px solid var(--color-border-tertiary)',
    borderRadius: 'var(--border-radius-xl)',
    boxShadow: 'var(--shadow-xl)',
    outline: 'none',
    animation: reduce ? undefined : 'gatewiseDialogIn 0.32s var(--ease-spring) both',
  }

  return createPortal(
    <>
      <OverlayKeyframes />
      <div style={backdrop} onMouseDown={onClose}>
        <div
          ref={panelRef}
          role="dialog"
          aria-modal="true"
          aria-labelledby={title ? labelId : undefined}
          tabIndex={-1}
          style={panel}
          // Stop clicks inside the panel from bubbling to the backdrop's close handler.
          onMouseDown={(e) => e.stopPropagation()}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 'var(--sp-3)',
              padding: 'var(--sp-5) var(--sp-6)',
              borderBottom: '0.5px solid var(--color-border-tertiary)',
            }}
          >
            <div id={labelId} style={{ fontSize: 16, fontWeight: 600 }}>
              {title}
            </div>
            <button
                type="button"
                onClick={onClose}
                aria-label={t('common.close')}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: 30,
                  height: 30,
                  minHeight: 0,
                  padding: 0,
                  borderRadius: 'var(--border-radius-md)',
                  color: 'var(--color-text-secondary)',
                }}
              >
                <i className="ti ti-x" aria-hidden />
              </button>
          </div>
          <div style={{ padding: 'var(--sp-6)', overflow: 'auto', flex: 1 }}>{children}</div>
          {actions && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'flex-end',
                gap: 'var(--sp-2)',
                padding: 'var(--sp-4) var(--sp-6)',
                borderTop: '0.5px solid var(--color-border-tertiary)',
              }}
            >
              {actions}
            </div>
          )}
        </div>
      </div>
    </>,
    document.body,
  )
}

// ───────────────────────────── Tooltip ─────────────────────────────

/** Wraps a child and reveals a small dark bubble on hover/focus, positioned to `side` (default
 * top) with a quick fade. Pure absolute positioning. `role="tooltip"`. */
export function Tooltip({
  label,
  children,
  side = 'top',
}: {
  label: ReactNode
  children: ReactNode
  side?: 'top' | 'bottom' | 'left' | 'right'
}) {
  const [shown, setShown] = useState(false)
  const tipId = useId()
  const reduce = prefersReducedMotion()

  const GAP = 8
  const pos: CSSProperties = {
    top: { bottom: '100%', left: '50%', transform: 'translateX(-50%)', marginBottom: GAP },
    bottom: { top: '100%', left: '50%', transform: 'translateX(-50%)', marginTop: GAP },
    left: { right: '100%', top: '50%', transform: 'translateY(-50%)', marginRight: GAP },
    right: { left: '100%', top: '50%', transform: 'translateY(-50%)', marginLeft: GAP },
  }[side]

  return (
    <span
      style={{ position: 'relative', display: 'inline-flex' }}
      onMouseEnter={() => setShown(true)}
      onMouseLeave={() => setShown(false)}
      onFocus={() => setShown(true)}
      onBlur={() => setShown(false)}
      aria-describedby={shown ? tipId : undefined}
    >
      {children}
      {shown && (
        <>
          <OverlayKeyframes />
          <span
            id={tipId}
            role="tooltip"
            style={{
              position: 'absolute',
              zIndex: 1100,
              ...pos,
              pointerEvents: 'none',
              whiteSpace: 'nowrap',
              maxWidth: 240,
              padding: '5px 9px',
              fontSize: 12,
              lineHeight: 1.4,
              color: 'var(--color-background-primary)',
              background: 'var(--color-text-primary)',
              borderRadius: 'var(--border-radius-md)',
              boxShadow: 'var(--shadow-md)',
              animation: reduce ? undefined : 'gatewiseFade 0.14s var(--ease-out)',
            }}
          >
            {label}
          </span>
        </>
      )}
    </span>
  )
}

// ───────────────────────────── Menu ─────────────────────────────

type MenuItem = { label: ReactNode; icon?: string; onClick: () => void; danger?: boolean }

/** A click-to-toggle dropdown anchored to `trigger`. Closes on click-outside and ESC; supports
 * up/down arrow navigation + Enter/Space to activate. `role="menu"` / `menuitem`. */
export function Menu({
  trigger,
  items,
  align = 'left',
}: {
  trigger: ReactNode
  items: MenuItem[]
  align?: 'left' | 'right'
}) {
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const rootRef = useRef<HTMLDivElement>(null)
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([])
  const reduce = prefersReducedMotion()

  // Click-outside + ESC closes the menu while open.
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  // Keep DOM focus in step with the active index.
  useEffect(() => {
    if (open && active >= 0) itemRefs.current[active]?.focus()
  }, [open, active])

  function openMenu() {
    setActive(0)
    setOpen(true)
  }

  function onListKey(e: ReactKeyboardEvent) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActive((i) => (i + 1) % items.length)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((i) => (i - 1 + items.length) % items.length)
    } else if (e.key === 'Home') {
      e.preventDefault()
      setActive(0)
    } else if (e.key === 'End') {
      e.preventDefault()
      setActive(items.length - 1)
    }
  }

  function run(item: MenuItem) {
    setOpen(false)
    item.onClick()
  }

  return (
    <div ref={rootRef} style={{ position: 'relative', display: 'inline-flex' }}>
      <OverlayKeyframes />
      <span
        onClick={() => (open ? setOpen(false) : openMenu())}
        aria-haspopup="menu"
        aria-expanded={open}
        style={{ display: 'inline-flex' }}
      >
        {trigger}
      </span>
      {open && (
        <div
          role="menu"
          onKeyDown={onListKey}
          style={{
            position: 'absolute',
            top: '100%',
            [align]: 0,
            zIndex: 1050,
            marginTop: 'var(--sp-1)',
            minWidth: 180,
            padding: 'var(--sp-1)',
            background: 'var(--color-background-primary)',
            border: '0.5px solid var(--color-border-tertiary)',
            borderRadius: 'var(--border-radius-lg)',
            boxShadow: 'var(--shadow-lg)',
            animation: reduce ? undefined : 'gatewiseMenuIn 0.16s var(--ease-out)',
          }}
        >
          {items.map((item, i) => (
            <button
              key={i}
              ref={(el) => {
                itemRefs.current[i] = el
              }}
              role="menuitem"
              type="button"
              tabIndex={i === active ? 0 : -1}
              onClick={() => run(item)}
              onMouseEnter={() => setActive(i)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 'var(--sp-2)',
                width: '100%',
                minHeight: 0,
                padding: '8px 10px',
                border: 'none',
                borderRadius: 'var(--border-radius-md)',
                fontSize: 13,
                textAlign: 'left',
                color: item.danger ? 'var(--color-text-danger)' : 'var(--color-text-primary)',
                background:
                  i === active ? 'var(--color-background-secondary)' : 'transparent',
              }}
            >
              {item.icon && <i className={`ti ${item.icon}`} aria-hidden />}
              <span>{item.label}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

// ───────────────────────────── Toasts ─────────────────────────────

type ToastRecord = { id: number; message: ReactNode; tone: Tone; leaving: boolean }

type ToastApi = { toast: (message: ReactNode, tone?: Tone) => void }

const ToastContext = createContext<ToastApi | null>(null)

const TOAST_DURATION = 3500
const TOAST_EXIT = 280

/** Wrap the app (or a subtree) so descendants can raise toasts via {@link useToast}. Renders a
 * fixed top-right stack, portalled to `document.body`. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastRecord[]>([])
  const timers = useRef<Record<number, number>>({})
  const reduce = prefersReducedMotion()

  const remove = useCallback((id: number) => {
    // Trigger the exit animation, then unmount after it finishes.
    setToasts((list) => list.map((t) => (t.id === id ? { ...t, leaving: true } : t)))
    window.clearTimeout(timers.current[id])
    timers.current[id] = window.setTimeout(() => {
      setToasts((list) => list.filter((t) => t.id !== id))
      delete timers.current[id]
    }, TOAST_EXIT)
  }, [])

  const toast = useCallback(
    (message: ReactNode, tone: Tone = 'info') => {
      const id = Date.now() + Math.random()
      setToasts((list) => [...list, { id, message, tone, leaving: false }])
      timers.current[id] = window.setTimeout(() => remove(id), TOAST_DURATION)
    },
    [remove],
  )

  // Clear any pending timers on unmount.
  useEffect(() => {
    const map = timers.current
    return () => {
      Object.values(map).forEach((t) => window.clearTimeout(t))
    }
  }, [])

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      {createPortal(
        <>
          <OverlayKeyframes />
          <div
            aria-live="polite"
            aria-atomic="false"
            style={{
              position: 'fixed',
              top: 'var(--sp-5)',
              right: 'var(--sp-5)',
              zIndex: 1200,
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--sp-2)',
              maxWidth: 360,
              pointerEvents: 'none',
            }}
          >
            {toasts.map((t) => (
              <div
                key={t.id}
                role="status"
                onClick={() => remove(t.id)}
                style={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 'var(--sp-2)',
                  padding: 'var(--sp-3) var(--sp-4)',
                  background: 'var(--color-background-primary)',
                  border: '0.5px solid var(--color-border-tertiary)',
                  borderRadius: 'var(--border-radius-lg)',
                  boxShadow: 'var(--shadow-lg)',
                  fontSize: 13,
                  color: 'var(--color-text-primary)',
                  cursor: 'pointer',
                  pointerEvents: 'auto',
                  animation: reduce
                    ? undefined
                    : t.leaving
                      ? `gatewiseToastOut ${TOAST_EXIT}ms var(--ease-out) both`
                      : 'gatewiseToastIn 0.34s var(--ease-spring) both',
                }}
              >
                <i
                  className={`ti ${TONE_ICON[t.tone]}`}
                  aria-hidden
                  style={{ color: `var(--color-text-${t.tone})`, fontSize: 17, marginTop: 1 }}
                />
                <span style={{ flex: 1 }}>{t.message}</span>
              </div>
            ))}
          </div>
        </>,
        document.body,
      )}
    </ToastContext.Provider>
  )
}

/** Returns `{ toast }` for raising notifications. Must be used under a {@link ToastProvider}. */
export function useToast(): ToastApi {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used within a <ToastProvider>')
  return ctx
}

// ───────────────────────────── Shared keyframes ─────────────────────────────

/** The overlay keyframes, emitted inline so the file is self-contained and does not rely on global
 * CSS loading first. Rendered alongside each open overlay; duplicate identical `@keyframes` blocks
 * are harmless. Disabled wholesale under prefers-reduced-motion by the global guard in tokens.css. */
function OverlayKeyframes() {
  return (
    <style>{`
      @keyframes gatewiseFade { from { opacity: 0 } to { opacity: 1 } }
      @keyframes gatewiseDialogIn {
        from { opacity: 0; transform: translateY(8px) scale(0.96) }
        to { opacity: 1; transform: translateY(0) scale(1) }
      }
      @keyframes gatewiseMenuIn {
        from { opacity: 0; transform: translateY(-6px) scale(0.98) }
        to { opacity: 1; transform: translateY(0) scale(1) }
      }
      @keyframes gatewiseToastIn {
        from { opacity: 0; transform: translateX(24px) scale(0.96) }
        to { opacity: 1; transform: translateX(0) scale(1) }
      }
      @keyframes gatewiseToastOut {
        from { opacity: 1; transform: translateX(0) scale(1) }
        to { opacity: 0; transform: translateX(24px) scale(0.96) }
      }
    `}</style>
  )
}
