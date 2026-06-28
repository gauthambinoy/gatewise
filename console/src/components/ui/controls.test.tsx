import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Button, IconButton, Switch, Checkbox, TextField, SearchInput } from './controls'
import { renderWithI18n } from '../../test/utils'

describe('Button', () => {
  it('renders its label as an accessible button defaulting to type="button"', () => {
    renderWithI18n(<Button>Save changes</Button>)
    const btn = screen.getByRole('button', { name: 'Save changes' })
    expect(btn).toBeInTheDocument()
    expect(btn).toHaveAttribute('type', 'button')
  })

  it('forwards type="submit"', () => {
    renderWithI18n(<Button type="submit">Send</Button>)
    expect(screen.getByRole('button', { name: 'Send' })).toHaveAttribute('type', 'submit')
  })

  it('calls onClick when activated', async () => {
    const onClick = vi.fn()
    renderWithI18n(<Button onClick={onClick}>Go</Button>)
    await userEvent.click(screen.getByRole('button', { name: 'Go' }))
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('is disabled and inert when disabled', async () => {
    const onClick = vi.fn()
    renderWithI18n(
      <Button disabled onClick={onClick}>
        Nope
      </Button>,
    )
    const btn = screen.getByRole('button', { name: 'Nope' })
    expect(btn).toBeDisabled()
    await userEvent.click(btn)
    expect(onClick).not.toHaveBeenCalled()
  })

  it('signals a busy, non-interactive state while loading', async () => {
    const onClick = vi.fn()
    renderWithI18n(
      <Button loading onClick={onClick}>
        Submit
      </Button>,
    )
    const btn = screen.getByRole('button', { name: 'Submit' })
    expect(btn).toHaveAttribute('aria-busy', 'true')
    expect(btn).toBeDisabled()
    await userEvent.click(btn)
    expect(onClick).not.toHaveBeenCalled()
  })
})

describe('IconButton', () => {
  it('exposes its label as the accessible name and title', () => {
    renderWithI18n(<IconButton icon="ti-trash" label="Delete row" />)
    const btn = screen.getByRole('button', { name: 'Delete row' })
    expect(btn).toHaveAttribute('title', 'Delete row')
  })

  it('fires onClick when enabled', async () => {
    const onClick = vi.fn()
    renderWithI18n(<IconButton icon="ti-x" label="Close" onClick={onClick} />)
    await userEvent.click(screen.getByRole('button', { name: 'Close' }))
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('does not fire onClick when disabled', async () => {
    const onClick = vi.fn()
    renderWithI18n(<IconButton icon="ti-x" label="Close" onClick={onClick} disabled />)
    const btn = screen.getByRole('button', { name: 'Close' })
    expect(btn).toBeDisabled()
    await userEvent.click(btn)
    expect(onClick).not.toHaveBeenCalled()
  })
})

describe('Switch', () => {
  it('reflects the checked state through aria-checked', () => {
    renderWithI18n(<Switch checked onChange={() => {}} label="Enabled" />)
    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'true')
  })

  it('toggles to the opposite value on click', async () => {
    const onChange = vi.fn()
    renderWithI18n(<Switch checked={false} onChange={onChange} />)
    await userEvent.click(screen.getByRole('switch'))
    expect(onChange).toHaveBeenCalledWith(true)
  })
})

describe('Checkbox', () => {
  it('reports the new checked value via onChange', async () => {
    const onChange = vi.fn()
    renderWithI18n(<Checkbox checked={false} onChange={onChange} label="Accept" />)
    await userEvent.click(screen.getByLabelText('Accept'))
    expect(onChange).toHaveBeenCalledWith(true)
  })
})

describe('TextField', () => {
  it('associates its label and reports typed input', async () => {
    const onChange = vi.fn()
    renderWithI18n(<TextField label="Email" value="" onChange={onChange} />)
    const input = screen.getByLabelText('Email')
    await userEvent.type(input, 'a')
    expect(onChange).toHaveBeenCalledWith('a')
  })

  it('surfaces an error with aria-invalid and an accessible description', () => {
    renderWithI18n(<TextField label="Key" value="x" onChange={() => {}} error="Required" />)
    const input = screen.getByLabelText('Key')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input.getAttribute('aria-describedby')).toBeTruthy()
    expect(screen.getByText('Required')).toBeInTheDocument()
  })
})

describe('SearchInput', () => {
  it('renders a searchbox and clears its value on demand', async () => {
    const onChange = vi.fn()
    renderWithI18n(<SearchInput value="hello" onChange={onChange} />)
    expect(screen.getByRole('searchbox')).toHaveValue('hello')
    await userEvent.click(screen.getByRole('button', { name: 'Clear search' }))
    expect(onChange).toHaveBeenCalledWith('')
  })

  it('submits on the Enter key', async () => {
    const onSubmit = vi.fn()
    renderWithI18n(<SearchInput value="q" onChange={() => {}} onSubmit={onSubmit} />)
    const box = screen.getByRole('searchbox')
    await userEvent.click(box)
    await userEvent.keyboard('{Enter}')
    expect(onSubmit).toHaveBeenCalledTimes(1)
  })
})
