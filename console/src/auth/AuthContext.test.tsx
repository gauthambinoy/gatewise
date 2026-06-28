import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuthProvider, useAuth } from './AuthContext'
import { getApiKey, setApiKey } from '../lib/api'

// A minimal probe that surfaces the auth state and exposes the login action.
function Probe() {
  const { tenant, loading, login } = useAuth()
  return (
    <div>
      <span data-testid="loading">{String(loading)}</span>
      <span data-testid="tenant">{tenant ? tenant.name : 'none'}</span>
      <button onClick={() => login('new-key').catch(() => {})}>login</button>
    </div>
  )
}

/** Builds a Response-shaped stub matching the few fields the api client touches. */
function jsonResponse(status: number, body: unknown): Response {
  return {
    status,
    ok: status >= 200 && status < 300,
    statusText: status === 200 ? 'OK' : 'Error',
    text: async () => (body === undefined ? '' : JSON.stringify(body)),
  } as unknown as Response
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('AuthProvider', () => {
  it('resolves the tenant from /v1/me on boot when a key is stored', async () => {
    setApiKey('stored-key')
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(200, { id: 't1', name: 'Acme', slug: 'acme' }))
    vi.stubGlobal('fetch', fetchMock)

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    await screen.findByText('Acme')
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/v1/me')
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer stored-key')
    expect(screen.getByTestId('loading')).toHaveTextContent('false')
  })

  it('clears the stored key and stays signed out after a 401 on boot', async () => {
    setApiKey('bad-key')
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(401, { error: { message: 'unauthorized' } }))
    vi.stubGlobal('fetch', fetchMock)

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    expect(screen.getByTestId('tenant')).toHaveTextContent('none')
    expect(getApiKey()).toBeNull()
  })

  it('does not call the API when no key is stored', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    expect(fetchMock).not.toHaveBeenCalled()
    expect(screen.getByTestId('tenant')).toHaveTextContent('none')
  })

  it('stores the key and resolves the tenant on a successful login', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(200, { id: 't2', name: 'Globex', slug: 'globex' }))
    vi.stubGlobal('fetch', fetchMock)

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    await userEvent.click(screen.getByRole('button', { name: 'login' }))

    await screen.findByText('Globex')
    expect(getApiKey()).toBe('new-key')
  })

  it('clears the key when a login is rejected by the API', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(401, { message: 'nope' }))
    vi.stubGlobal('fetch', fetchMock)

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    await userEvent.click(screen.getByRole('button', { name: 'login' }))

    await waitFor(() => expect(getApiKey()).toBeNull())
    expect(screen.getByTestId('tenant')).toHaveTextContent('none')
  })
})
