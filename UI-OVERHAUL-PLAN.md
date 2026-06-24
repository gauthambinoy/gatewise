# Console v2 — UI overhaul plan (Phase 13)

Goal: a polished, MUI-grade console — many small, accurate, reusable components — internationalized
and formatted for a 200M-strong European audience, with WCAG-AA accessibility.

## 1. Design system v2 (tokens)
- 8px spacing scale, 5 elevation levels, type scale (display→caption), radii, motion tokens.
- Semantic colour roles already exist (light/dark) — extend with `--primary`, `--surface-*`, focus ring.

## 2. Component library (`console/src/components/ui/`)  — the "max small components"
Button · IconButton · TextField · Select · SearchInput · Switch · Checkbox ·
Card (+Header/Body) · StatCard · Chip · Badge · Avatar · Alert/Banner ·
Tabs · DataTable · Pagination · Dialog/Modal · Tooltip · Menu/Dropdown ·
Toast (provider+hook) · ProgressBar · Skeleton · Spinner · Breadcrumbs · EmptyState · ErrorState.
Each: variants + sizes, keyboard-accessible, ARIA, focus-visible, reduced-motion safe.

## 3. Internationalization (European)
- `lib/i18n.tsx`: provider + `useT()` + dictionaries **EN · DE · FR · ES · IT** (covers most of the EU).
- Locale-aware formatting: dates, numbers, and **EUR** currency via `Intl` (per active locale).
- `LanguageSwitcher` in the top bar (persisted). All visible strings keyed.

## 4. Re-skin every page with the library + i18n
Login · AppShell · Dashboard · AuditLog · RequestDetail · Policies · PolicyEditor ·
ModelsRouting · UsageCost · Users · TeamRoles · ApiKeys · Settings.

## 5. Accessibility + polish
WCAG-AA contrast, full keyboard nav, ARIA roles/labels, skip-link, motion-reduced support,
responsive layout, consistent empty/loading/error states.

## 6. Ship
Build (tsc+vite) green → redeploy to cobra → capture light+dark screenshots + video.

## Execution
Foundation (tokens, component library, i18n) built cohesively first; page re-skins fanned out to
parallel agents against the finished library; one build + deploy at the end.
