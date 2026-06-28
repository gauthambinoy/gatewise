import { describe, it, expect } from 'vitest'
import { money, num, dt, clock } from '../components/ui'
import { intlLocale } from './i18n'
import { applyLocale } from '../test/utils'

// These formatters read the module-level active Intl locale, which `applyLocale` sets by mounting
// the real I18nProvider — exactly how the app switches language. Each test pins its own locale so
// the suite is order-independent.

describe('locale-aware formatters', () => {
  it('renders an em dash for null / undefined values', () => {
    applyLocale('en')
    expect(money(null)).toBe('—')
    expect(money(undefined)).toBe('—')
    expect(num(null)).toBe('—')
    expect(num(undefined)).toBe('—')
  })

  it('groups thousands the British way in en-GB', () => {
    applyLocale('en')
    expect(intlLocale()).toBe('en-GB')
    expect(num(1234567)).toBe('1,234,567')
  })

  it('groups thousands the German way in de-DE', () => {
    applyLocale('de')
    expect(intlLocale()).toBe('de-DE')
    expect(num(1234567)).toBe('1.234.567')
  })

  it('formats USD currency with British grouping and a decimal point', () => {
    applyLocale('en')
    const out = money(1234.5)
    expect(out).toContain('1,234.5')
    expect(out).toContain('$')
  })

  it('formats USD currency with German grouping and a decimal comma', () => {
    applyLocale('de')
    const out = money(1234.5)
    expect(out).toMatch(/1\.234/)
    expect(out).toMatch(/,50/)
    expect(out).toContain('$')
  })

  it('formats USD currency with a French decimal comma', () => {
    applyLocale('fr')
    const out = money(1234.5)
    expect(out).toMatch(/234,5/)
    expect(out).toContain('$')
  })

  it('passes an unparseable timestamp through unchanged', () => {
    expect(dt('not-a-real-date')).toBe('not-a-real-date')
    expect(clock('still-not-a-date')).toBe('still-not-a-date')
  })

  it('renders a parseable ISO timestamp as a localized string', () => {
    applyLocale('en')
    const out = dt('2024-01-15T10:30:00Z')
    expect(out).not.toBe('2024-01-15T10:30:00Z')
    expect(out).toMatch(/\d/)
    expect(out.length).toBeGreaterThan(0)
  })
})
