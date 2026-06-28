import { describe, it, expect, vi } from 'vitest'
import { act, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DataTable, Pagination, type Column } from './data'
import { renderWithI18n } from '../../test/utils'

interface Row {
  id: number
  name: string
}

const columns: Column<Row>[] = [
  { key: 'name', header: 'Name' },
  { key: 'id', header: 'ID' },
]

describe('Pagination', () => {
  it('renders the total count and the current page summary', () => {
    renderWithI18n(<Pagination page={0} pageCount={5} total={1234} onPage={() => {}} />)
    expect(screen.getByText(/1,234/)).toBeInTheDocument()
    expect(screen.getByText(/page 1 of 5/)).toBeInTheDocument()
  })

  it('disables First / Previous on the first page', () => {
    renderWithI18n(<Pagination page={0} pageCount={3} onPage={() => {}} />)
    expect(screen.getByRole('button', { name: 'First page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeEnabled()
  })

  it('disables Next / Last on the final page', () => {
    renderWithI18n(<Pagination page={2} pageCount={3} onPage={() => {}} />)
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Last page' })).toBeDisabled()
  })

  it('advances to the next page when Next is clicked', async () => {
    const onPage = vi.fn()
    renderWithI18n(<Pagination page={1} pageCount={4} onPage={onPage} />)
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }))
    expect(onPage).toHaveBeenCalledWith(2)
  })
})

describe('DataTable', () => {
  const rows: Row[] = [
    { id: 1, name: 'Alpha' },
    { id: 2, name: 'Beta' },
  ]

  it('renders column headers and one row per record', () => {
    renderWithI18n(<DataTable columns={columns} rows={rows} rowKey={(r) => r.id} />)
    expect(screen.getByRole('columnheader', { name: 'Name' })).toBeInTheDocument()
    expect(screen.getByText('Alpha')).toBeInTheDocument()
    expect(screen.getByText('Beta')).toBeInTheDocument()
    // header row + two data rows
    expect(screen.getAllByRole('row')).toHaveLength(3)
  })

  it('renders the empty fallback and no table when there are no rows', () => {
    renderWithI18n(
      <DataTable
        columns={columns}
        rows={[]}
        rowKey={(r) => r.id}
        empty={<div>Nothing here</div>}
      />,
    )
    expect(screen.getByText('Nothing here')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('invokes onRowClick when a row is clicked', async () => {
    const onRowClick = vi.fn()
    const single: Row[] = [{ id: 7, name: 'Clickable' }]
    renderWithI18n(
      <DataTable columns={columns} rows={single} rowKey={(r) => r.id} onRowClick={onRowClick} />,
    )
    await userEvent.click(screen.getByText('Clickable'))
    expect(onRowClick).toHaveBeenCalledWith(single[0])
  })

  it('activates a focused row with the Enter key', async () => {
    const onRowClick = vi.fn()
    const single: Row[] = [{ id: 9, name: 'KeyRow' }]
    renderWithI18n(
      <DataTable columns={columns} rows={single} rowKey={(r) => r.id} onRowClick={onRowClick} />,
    )
    const row = screen.getByText('KeyRow').closest('[role="row"]') as HTMLElement
    act(() => row.focus())
    await userEvent.keyboard('{Enter}')
    expect(onRowClick).toHaveBeenCalledWith(single[0])
  })
})
