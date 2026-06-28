import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StatCard, Alert, Chip, ProgressBar, Avatar } from './surfaces'
import { renderWithI18n } from '../../test/utils'

describe('StatCard', () => {
  it('shows its label and value', () => {
    renderWithI18n(<StatCard label="Total requests" value="1,024" />)
    expect(screen.getByText('Total requests')).toBeInTheDocument()
    expect(screen.getByText('1,024')).toBeInTheDocument()
  })

  it('renders a trend value', () => {
    renderWithI18n(<StatCard label="Cost" value="$10" trend={{ dir: 'up', value: '+12%' }} />)
    expect(screen.getByText('+12%')).toBeInTheDocument()
  })
})

describe('Alert', () => {
  it('uses role="alert" for the danger tone', () => {
    renderWithI18n(
      <Alert tone="danger" title="Failed">
        Something broke
      </Alert>,
    )
    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('Failed')
    expect(alert).toHaveTextContent('Something broke')
  })

  it('uses role="status" for non-danger tones', () => {
    renderWithI18n(<Alert tone="info">All good</Alert>)
    expect(screen.getByRole('status')).toHaveTextContent('All good')
  })

  it('fires onClose from its dismiss button', async () => {
    const onClose = vi.fn()
    renderWithI18n(
      <Alert tone="warning" onClose={onClose}>
        Heads up
      </Alert>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})

describe('Chip', () => {
  it('renders its content and removes on demand', async () => {
    const onRemove = vi.fn()
    renderWithI18n(<Chip onRemove={onRemove}>email</Chip>)
    expect(screen.getByText('email')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Remove' }))
    expect(onRemove).toHaveBeenCalledTimes(1)
  })
})

describe('ProgressBar', () => {
  it('clamps an out-of-range value up to 100', () => {
    renderWithI18n(<ProgressBar value={150} />)
    const bar = screen.getByRole('progressbar')
    expect(bar).toHaveAttribute('aria-valuenow', '100')
    expect(bar).toHaveAttribute('aria-valuemin', '0')
    expect(bar).toHaveAttribute('aria-valuemax', '100')
  })

  it('clamps a negative value down to 0', () => {
    renderWithI18n(<ProgressBar value={-25} />)
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '0')
  })
})

describe('Avatar', () => {
  it('derives two initials from a full name', () => {
    renderWithI18n(<Avatar name="Ada Lovelace" />)
    const av = screen.getByRole('img', { name: 'Ada Lovelace' })
    expect(av).toHaveTextContent('AL')
  })

  it('falls back to a generic accessible label without a name', () => {
    renderWithI18n(<Avatar />)
    expect(screen.getByRole('img', { name: 'Avatar' })).toBeInTheDocument()
  })
})
