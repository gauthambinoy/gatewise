import { useEffect, useRef, useState } from 'react'
import type { CSSProperties, KeyboardEvent, ReactNode } from 'react'

/* ------------------------------------------------------------------------------------------------
 * Data-display primitives for the Auvex console.
 *
 * Pure React 18 + TypeScript — no MUI, no external UI library. Everything is themed exclusively
 * through the design tokens in styles/tokens.css + styles/animations.css (never hardcoded colours)
 * and uses the existing `.thead` / `.row` grid-table look the app already renders.
 * ---------------------------------------------------------------------------------------------- */

const reduceMotion = () =>
  typeof window !== 'undefined' &&
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true

/* ============================================================================================== */
/* DataTable                                                                                       */
/* ============================================================================================== */

export interface Column<T> {
  key: string
  header: ReactNode
  render?: (row: T) => ReactNode
  width?: string
  align?: 'left' | 'right' | 'center'
}

interface DataTableProps<T> {
  columns: Column<T>[]
  rows: T[]
  rowKey: (row: T) => string | number
  onRowClick?: (row: T) => void
  empty?: ReactNode
  dense?: boolean
}

/** A generic, CSS-grid table that matches the app's `.thead` / `.row` look. Columns size from
 * each `width` (default `1fr`); cells respect per-column `align`. Rows lift + tint on hover and,
 * when `onRowClick` is given, become keyboard-activatable (Tab + Enter/Space) with a pointer. */
export function DataTable<T>({
  columns,
  rows,
  rowKey,
  onRowClick,
  empty,
  dense,
}: DataTableProps<T>): JSX.Element {
  const reduce = reduceMotion()
  const [hovered, setHovered] = useState<string | number | null>(null)

  const gridTemplateColumns = columns.map((c) => c.width ?? '1fr').join(' ')
  const clickable = Boolean(onRowClick)
  const cellPad = dense ? '6px 8px' : '10px 8px'

  const cellStyle = (col: Column<T>): CSSProperties => ({
    textAlign: col.align ?? 'left',
    justifyContent:
      col.align === 'right' ? 'flex-end' : col.align === 'center' ? 'center' : 'flex-start',
    minWidth: 0,
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  })

  function activate(row: T, e: KeyboardEvent<HTMLDivElement>) {
    if (!onRowClick) return
    if (e.key === 'Enter' || e.key === ' ' || e.key === 'Spacebar') {
      e.preventDefault()
      onRowClick(row)
    }
  }

  if (rows.length === 0) {
    return <>{empty ?? null}</>
  }

  return (
    <div role="table" aria-rowcount={rows.length}>
      <div
        role="row"
        className="thead"
        style={{ display: 'grid', gridTemplateColumns, gap: 'var(--sp-2)' }}
      >
        {columns.map((col) => (
          <span
            key={col.key}
            role="columnheader"
            style={{
              textAlign: col.align ?? 'left',
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
              color: 'var(--color-text-tertiary)',
            }}
          >
            {col.header}
          </span>
        ))}
      </div>

      {rows.map((row) => {
        const key = rowKey(row)
        const isHover = hovered === key
        return (
          <div
            key={key}
            role="row"
            className="row"
            tabIndex={clickable ? 0 : undefined}
            aria-label={clickable ? 'Row, press Enter to open' : undefined}
            onClick={onRowClick ? () => onRowClick(row) : undefined}
            onKeyDown={onRowClick ? (e) => activate(row, e) : undefined}
            onMouseEnter={() => setHovered(key)}
            onMouseLeave={() => setHovered((h) => (h === key ? null : h))}
            onFocus={() => setHovered(key)}
            onBlur={() => setHovered((h) => (h === key ? null : h))}
            style={{
              display: 'grid',
              gridTemplateColumns,
              gap: 'var(--sp-2)',
              padding: cellPad,
              cursor: clickable ? 'pointer' : 'default',
              borderRadius: 'var(--border-radius-md)',
              background: isHover ? 'var(--color-background-secondary)' : 'transparent',
              transform: !reduce && isHover ? 'translateX(2px)' : 'translateX(0)',
              transition: reduce
                ? undefined
                : 'background-color 0.25s var(--ease-out), transform 0.25s var(--ease-spring)',
            }}
          >
            {columns.map((col) => (
              <span key={col.key} role="cell" style={{ ...cellStyle(col), display: 'flex', alignItems: 'center' }}>
                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '100%' }}>
                  {col.render ? col.render(row) : (row as Record<string, ReactNode>)[col.key]}
                </span>
              </span>
            ))}
          </div>
        )
      })}
    </div>
  )
}

/* ============================================================================================== */
/* Pagination                                                                                      */
/* ============================================================================================== */

interface PaginationProps {
  /** 0-based current page. */
  page: number
  pageCount: number
  total?: number
  onPage: (p: number) => void
  itemLabel?: string
}

/** Left: "{total} {itemLabel} · page {page+1} of {pageCount}". Right: First / Prev / Next / Last,
 * each disabled at the relevant boundary. Mirrors the app's `.tnote` footer. */
export function Pagination({
  page,
  pageCount,
  total,
  onPage,
  itemLabel = 'items',
}: PaginationProps): JSX.Element {
  const count = Math.max(1, pageCount)
  const atStart = page <= 0
  const atEnd = page >= count - 1

  const btn: CSSProperties = { padding: '5px 9px', fontSize: 12, lineHeight: 1 }

  return (
    <div className="tnote">
      <span>
        {total !== undefined && (
          <>
            {total.toLocaleString()} {itemLabel} ·{' '}
          </>
        )}
        page {Math.min(page, count - 1) + 1} of {count}
      </span>
      <div style={{ display: 'flex', gap: 'var(--sp-1)' }} role="group" aria-label="Pagination">
        <button
          type="button"
          disabled={atStart}
          aria-label="First page"
          onClick={() => onPage(0)}
          style={btn}
        >
          <i className="ti ti-chevrons-left" aria-hidden />
        </button>
        <button
          type="button"
          disabled={atStart}
          aria-label="Previous page"
          onClick={() => onPage(page - 1)}
          style={btn}
        >
          <i className="ti ti-chevron-left" aria-hidden />
        </button>
        <button
          type="button"
          disabled={atEnd}
          aria-label="Next page"
          onClick={() => onPage(page + 1)}
          style={btn}
        >
          <i className="ti ti-chevron-right" aria-hidden />
        </button>
        <button
          type="button"
          disabled={atEnd}
          aria-label="Last page"
          onClick={() => onPage(count - 1)}
          style={btn}
        >
          <i className="ti ti-chevrons-right" aria-hidden />
        </button>
      </div>
    </div>
  )
}

/* ============================================================================================== */
/* Tabs                                                                                            */
/* ============================================================================================== */

interface Tab {
  value: string
  label: string
  icon?: string
}

interface TabsProps {
  tabs: Tab[]
  value: string
  onChange: (v: string) => void
}

/** A horizontal tab bar with an animated sliding underline beneath the active tab. Roving-tabindex
 * `role="tablist"`; Left/Right (+ Home/End) arrow keys move and select the focused tab. */
export function Tabs({ tabs, value, onChange }: TabsProps): JSX.Element {
  const reduce = reduceMotion()
  const listRef = useRef<HTMLDivElement>(null)
  const tabRefs = useRef<Record<string, HTMLButtonElement | null>>({})
  const [indicator, setIndicator] = useState<{ left: number; width: number }>({ left: 0, width: 0 })

  const activeIndex = Math.max(0, tabs.findIndex((t) => t.value === value))

  // Re-measure the active tab whenever the selection, tab set, or container size changes so the
  // underline always lines up — even after fonts load or the layout reflows.
  useEffect(() => {
    const el = tabRefs.current[value]
    const list = listRef.current
    if (!el || !list) return
    const measure = () => {
      const elBox = el.getBoundingClientRect()
      const listBox = list.getBoundingClientRect()
      setIndicator({ left: elBox.left - listBox.left, width: elBox.width })
    }
    measure()
    const ro = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(measure) : null
    ro?.observe(list)
    ro?.observe(el)
    return () => ro?.disconnect()
  }, [value, tabs])

  function onKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    let next = activeIndex
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') next = (activeIndex + 1) % tabs.length
    else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp')
      next = (activeIndex - 1 + tabs.length) % tabs.length
    else if (e.key === 'Home') next = 0
    else if (e.key === 'End') next = tabs.length - 1
    else return
    e.preventDefault()
    const target = tabs[next]
    if (!target) return
    onChange(target.value)
    tabRefs.current[target.value]?.focus()
  }

  return (
    <div
      ref={listRef}
      role="tablist"
      aria-orientation="horizontal"
      onKeyDown={onKeyDown}
      style={{
        position: 'relative',
        display: 'flex',
        gap: 'var(--sp-1)',
        borderBottom: '0.5px solid var(--color-border-tertiary)',
      }}
    >
      {tabs.map((t) => {
        const selected = t.value === value
        return (
          <button
            key={t.value}
            ref={(node) => {
              tabRefs.current[t.value] = node
            }}
            role="tab"
            id={`tab-${t.value}`}
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(t.value)}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 'var(--sp-2)',
              padding: 'var(--sp-2) var(--sp-3)',
              border: 'none',
              background: 'transparent',
              borderRadius: 'var(--border-radius-md) var(--border-radius-md) 0 0',
              fontSize: 13,
              fontWeight: selected ? 500 : 400,
              color: selected ? 'var(--color-text-primary)' : 'var(--color-text-secondary)',
              transition: reduce ? undefined : 'color 0.25s var(--ease-out)',
            }}
          >
            {t.icon && <i className={`ti ti-${t.icon}`} aria-hidden style={{ fontSize: 16 }} />}
            {t.label}
          </button>
        )
      })}
      <span
        aria-hidden
        style={{
          position: 'absolute',
          bottom: -0.5,
          left: 0,
          height: 2,
          width: indicator.width,
          transform: `translateX(${indicator.left}px)`,
          background: 'var(--primary)',
          borderRadius: '2px 2px 0 0',
          transition: reduce
            ? undefined
            : 'transform 0.32s var(--ease-spring), width 0.32s var(--ease-spring)',
          pointerEvents: 'none',
        }}
      />
    </div>
  )
}
