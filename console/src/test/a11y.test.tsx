import { describe, it, expect } from 'vitest'
import { renderWithI18n, axeViolations } from './utils'
import { Button, TextField } from '../components/ui/controls'
import { StatCard, Alert } from '../components/ui/surfaces'
import { Pagination } from '../components/ui/data'

// Accessibility smoke tests: render real primitives and assert axe-core finds zero violations.

describe('accessibility (axe-core)', () => {
  it('Button has no accessibility violations', async () => {
    const { container } = renderWithI18n(<Button variant="primary">Save</Button>)
    expect(await axeViolations(container)).toEqual([])
  })

  it('a labelled TextField has no accessibility violations', async () => {
    const { container } = renderWithI18n(
      <TextField label="Email" value="" onChange={() => {}} icon="ti-mail" />,
    )
    expect(await axeViolations(container)).toEqual([])
  })

  it('StatCard has no accessibility violations', async () => {
    const { container } = renderWithI18n(
      <StatCard label="Total cost" value="$1,024" icon="ti-coin" tone="success" />,
    )
    expect(await axeViolations(container)).toEqual([])
  })

  it('a dismissible Alert has no accessibility violations', async () => {
    const { container } = renderWithI18n(
      <Alert tone="danger" title="Error" onClose={() => {}}>
        Something went wrong
      </Alert>,
    )
    expect(await axeViolations(container)).toEqual([])
  })

  it('Pagination has no accessibility violations', async () => {
    const { container } = renderWithI18n(
      <Pagination page={1} pageCount={4} total={120} onPage={() => {}} />,
    )
    expect(await axeViolations(container)).toEqual([])
  })
})
