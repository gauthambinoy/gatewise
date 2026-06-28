import { describe, it, expect } from 'vitest'
import { DICTS, LOCALES } from './i18n'

type DictKey = keyof (typeof DICTS)['en']

const ALL_LOCALES = Object.keys(DICTS) as (keyof typeof DICTS)[]
const NON_EN = ALL_LOCALES.filter((l) => l !== 'en')
const EN_KEYS = (Object.keys(DICTS.en) as DictKey[]).sort()

/** Extracts the sorted set of `{placeholder}` tokens used in a string. */
function placeholders(s: string): string[] {
  return (s.match(/\{[a-zA-Z]+\}/g) ?? []).sort()
}

describe('i18n dictionaries', () => {
  it('ships exactly the five expected European locales', () => {
    expect(ALL_LOCALES.slice().sort()).toEqual(['de', 'en', 'es', 'fr', 'it'])
  })

  it('English (the source of truth) defines a substantial key set', () => {
    expect(EN_KEYS.length).toBeGreaterThan(100)
  })

  describe.each(NON_EN)('locale "%s"', (loc) => {
    const dict = DICTS[loc]
    const keys = (Object.keys(dict) as DictKey[]).sort()

    it('has exactly the same key set as English', () => {
      expect(keys).toEqual(EN_KEYS)
    })

    it('has no empty or whitespace-only translations', () => {
      const empty = (Object.entries(dict) as [DictKey, string][])
        .filter(([, v]) => v.trim() === '')
        .map(([k]) => k)
      expect(empty).toEqual([])
    })

    it('preserves every {placeholder} token from the English source', () => {
      const mismatched = EN_KEYS.filter(
        (k) => placeholders(DICTS.en[k]) .join(',') !== placeholders(dict[k]).join(','),
      )
      expect(mismatched).toEqual([])
    })
  })

  it('LOCALES metadata covers exactly the available dictionaries', () => {
    expect(LOCALES.map((l) => l.code).sort()).toEqual(ALL_LOCALES.slice().sort())
  })

  it('every LOCALES Intl tag is a real, resolvable BCP-47 locale', () => {
    for (const l of LOCALES) {
      expect(() => new Intl.NumberFormat(l.intl)).not.toThrow()
      expect(Intl.NumberFormat.supportedLocalesOf([l.intl])).toContain(l.intl)
    }
  })
})
