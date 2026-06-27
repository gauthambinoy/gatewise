import { useEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { LOCALES, useT, type Locale } from '../lib/i18n'

/** A flag + code dropdown in the top bar; switches the app language (persisted). The open list is a
 * keyboard-operable listbox — Up/Down (and Home/End) move the highlight, Enter/Space select, Esc
 * closes and returns focus to the trigger. */
export function LanguageSwitcher() {
  const { locale, setLocale, t } = useT()
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const ref = useRef<HTMLDivElement>(null)
  const optionRefs = useRef<(HTMLButtonElement | null)[]>([])
  const current = LOCALES.find((l) => l.code === locale) ?? LOCALES[0]

  useEffect(() => {
    function onDoc(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  // Move DOM focus to the highlighted option while the list is open.
  useEffect(() => {
    if (open) optionRefs.current[active]?.focus()
  }, [open, active])

  function openList() {
    setActive(Math.max(0, LOCALES.findIndex((l) => l.code === locale)))
    setOpen(true)
  }

  function choose(code: Locale) {
    setLocale(code)
    setOpen(false)
  }

  function onListKey(e: KeyboardEvent) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActive((i) => (i + 1) % LOCALES.length)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((i) => (i - 1 + LOCALES.length) % LOCALES.length)
    } else if (e.key === 'Home') {
      e.preventDefault()
      setActive(0)
    } else if (e.key === 'End') {
      e.preventDefault()
      setActive(LOCALES.length - 1)
    } else if (e.key === 'Escape') {
      e.preventDefault()
      setOpen(false)
      ref.current?.querySelector('button')?.focus()
    }
  }

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        className="themebtn"
        onClick={() => (open ? setOpen(false) : openList())}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={t('common.language')}
        title={t('common.language')}
      >
        <span style={{ fontSize: 15 }} aria-hidden>
          {current.flag}
        </span>
        <span style={{ textTransform: 'uppercase', fontSize: 12, letterSpacing: '0.04em' }}>
          {current.code}
        </span>
        <i className="ti ti-chevron-down" style={{ fontSize: 13 }} aria-hidden />
      </button>
      {open && (
        <ul
          role="listbox"
          aria-label={t('common.language')}
          onKeyDown={onListKey}
          style={{
            position: 'absolute',
            top: 'calc(100% + 6px)',
            right: 0,
            minWidth: 168,
            margin: 0,
            listStyle: 'none',
            background: 'var(--color-background-primary)',
            border: '0.5px solid var(--color-border-tertiary)',
            borderRadius: 'var(--border-radius-lg)',
            boxShadow: 'var(--shadow-lg)',
            padding: 4,
            zIndex: 50,
            animation: 'fadeUpBlur 0.22s var(--ease-out)',
          }}
        >
          {LOCALES.map((l, i) => (
            <li key={l.code} role="none">
              <button
                ref={(node) => {
                  optionRefs.current[i] = node
                }}
                role="option"
                aria-selected={l.code === locale}
                tabIndex={i === active ? 0 : -1}
                onClick={() => choose(l.code)}
                onMouseEnter={() => setActive(i)}
                style={{
                  width: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  padding: '8px 10px',
                  border: 'none',
                  borderRadius: 8,
                  background:
                    l.code === locale
                      ? 'var(--color-background-info)'
                      : i === active
                        ? 'var(--color-background-secondary)'
                        : 'transparent',
                  color: l.code === locale ? 'var(--color-text-info)' : 'var(--color-text-primary)',
                  fontSize: 13,
                  fontWeight: l.code === locale ? 600 : 400,
                  textAlign: 'left',
                }}
              >
                <span style={{ fontSize: 16 }} aria-hidden>
                  {l.flag}
                </span>
                {l.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
