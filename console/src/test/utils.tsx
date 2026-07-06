import { render, type RenderOptions, type RenderResult } from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'
import { axe } from 'jest-axe'
import { I18nProvider, type Locale } from '../lib/i18n'

/** Wraps the tree in the real I18nProvider so primitives that call `useT()` work as in the app. */
function Providers({ children }: { children: ReactNode }) {
  return <I18nProvider>{children}</I18nProvider>
}

/** `render`, but inside the app's I18nProvider. */
export function renderWithI18n(
  ui: ReactElement,
  options?: Omit<RenderOptions, 'wrapper'>,
): RenderResult {
  return render(ui, { wrapper: Providers, ...options })
}

/**
 * Activates a locale for the module-level `Intl` formatters (money/num/dt). Persists the choice the
 * same way the app does and mounts the provider, whose render syncs the active `Intl` locale.
 */
export function applyLocale(locale: Locale): void {
  localStorage.setItem('gatewise.locale', locale)
  render(<I18nProvider>{null}</I18nProvider>)
}

/**
 * Runs axe-core against a rendered container and returns the violations. Colour-contrast is disabled
 * because jsdom cannot compute layout/contrast, so that rule is meaningless here.
 */
export async function axeViolations(container: Element) {
  const results = await axe(container, {
    rules: { 'color-contrast': { enabled: false } },
  })
  return results.violations
}
