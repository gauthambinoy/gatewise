import { useEffect, useRef, useState } from 'react'
import { LOCALES, useT } from '../lib/i18n'

/** A flag + code dropdown in the top bar; switches the app language (persisted). */
export function LanguageSwitcher() {
  const { locale, setLocale } = useT()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const current = LOCALES.find((l) => l.code === locale) ?? LOCALES[0]

  useEffect(() => {
    function onDoc(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        className="themebtn"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label="Language"
        title="Language"
      >
        <span style={{ fontSize: 15 }}>{current.flag}</span>
        <span style={{ textTransform: 'uppercase', fontSize: 12, letterSpacing: '0.04em' }}>
          {current.code}
        </span>
        <i className="ti ti-chevron-down" style={{ fontSize: 13 }} aria-hidden />
      </button>
      {open && (
        <div
          role="listbox"
          style={{
            position: 'absolute',
            top: 'calc(100% + 6px)',
            right: 0,
            minWidth: 168,
            background: 'var(--color-background-primary)',
            border: '0.5px solid var(--color-border-tertiary)',
            borderRadius: 'var(--border-radius-lg)',
            boxShadow: 'var(--shadow-lg)',
            padding: 4,
            zIndex: 50,
            animation: 'fadeUpBlur 0.22s var(--ease-out)',
          }}
        >
          {LOCALES.map((l) => (
            <button
              key={l.code}
              role="option"
              aria-selected={l.code === locale}
              onClick={() => {
                setLocale(l.code)
                setOpen(false)
              }}
              style={{
                width: '100%',
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '8px 10px',
                border: 'none',
                borderRadius: 8,
                background: l.code === locale ? 'var(--color-background-info)' : 'transparent',
                color: l.code === locale ? 'var(--color-text-info)' : 'var(--color-text-primary)',
                fontSize: 13,
                fontWeight: l.code === locale ? 600 : 400,
                textAlign: 'left',
              }}
            >
              <span style={{ fontSize: 16 }}>{l.flag}</span>
              {l.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
