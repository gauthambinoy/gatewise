import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Badge, Stat, EmptyState, ErrorState, Loading, verdictTone } from './ui'
import { renderWithI18n } from '../test/utils'

describe('verdictTone', () => {
  it('maps verdicts to the correct badge tone', () => {
    expect(verdictTone('blocked')).toBe('danger')
    expect(verdictTone('redacted')).toBe('info')
    expect(verdictTone('allowed')).toBe('success')
  })

  it('treats any unknown verdict as success', () => {
    expect(verdictTone('something-else')).toBe('success')
  })
})

describe('Badge', () => {
  it('applies the tone modifier class alongside the base class', () => {
    const { container } = renderWithI18n(<Badge tone="danger">Blocked</Badge>)
    const badge = container.querySelector('.badge')
    expect(badge).toHaveClass('badge', 'badge-danger')
    expect(badge).toHaveTextContent('Blocked')
  })

  it('uses only the base class when no tone is given', () => {
    const { container } = renderWithI18n(<Badge>Plain</Badge>)
    const badge = container.querySelector('.badge')
    expect(badge?.className).toBe('badge')
  })
})

describe('Stat', () => {
  it('renders a label and value', () => {
    renderWithI18n(<Stat label="Allowed" value="42" />)
    expect(screen.getByText('Allowed')).toBeInTheDocument()
    expect(screen.getByText('42')).toBeInTheDocument()
  })
})

describe('EmptyState', () => {
  it('renders its title, message and action', () => {
    renderWithI18n(
      <EmptyState
        title="No policies yet"
        message="Add a rule to get started"
        action={<button>New policy</button>}
      />,
    )
    expect(screen.getByText('No policies yet')).toBeInTheDocument()
    expect(screen.getByText('Add a rule to get started')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'New policy' })).toBeInTheDocument()
  })
})

describe('ErrorState', () => {
  it('renders an alert and fires its retry handler', async () => {
    const onRetry = vi.fn()
    renderWithI18n(<ErrorState message="Boom" onRetry={onRetry} />)
    expect(screen.getByRole('alert')).toHaveTextContent('Boom')
    await userEvent.click(screen.getByRole('button', { name: /Retry/ }))
    expect(onRetry).toHaveBeenCalledTimes(1)
  })
})

describe('Loading', () => {
  it('renders a polite status region with the default i18n label', () => {
    renderWithI18n(<Loading />)
    const status = screen.getByRole('status')
    expect(status).toHaveTextContent('Loading')
    expect(status).toHaveAttribute('aria-live', 'polite')
  })
})
