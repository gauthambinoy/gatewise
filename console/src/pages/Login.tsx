import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { ApiError, api } from '../lib/api'
import type { SsoProvider } from '../lib/types'

const PROVIDER_ICON: Record<string, string> = { google: 'ti-brand-google', okta: 'ti-key' }
const DEMO_KEY = 'auvex_demo_key'

export function Login() {
  const { login } = useAuth()
  const [key, setKey] = useState('')
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const [demoBusy, setDemoBusy] = useState(false)
  const [providers, setProviders] = useState<SsoProvider[]>([])

  useEffect(() => {
    api.providers().then(setProviders).catch(() => setProviders([]))
  }, [])

  function messageFor(err: unknown, fallback: string): string {
    if (err instanceof ApiError && err.status === 401) return 'That API key was not recognized.'
    return err instanceof Error ? err.message : fallback
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(undefined)
    setBusy(true)
    try {
      await login(key)
    } catch (err) {
      setError(messageFor(err, 'Sign-in failed.'))
    } finally {
      setBusy(false)
    }
  }

  async function tryDemo() {
    setError(undefined)
    setDemoBusy(true)
    try {
      await login(DEMO_KEY)
    } catch {
      setError('The demo sandbox isn’t available on this server.')
    } finally {
      setDemoBusy(false)
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--color-background-secondary)',
        padding: 20,
      }}
    >
      <div className="card anim" style={{ width: 360, padding: '28px 26px' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 9,
            justifyContent: 'center',
            marginBottom: 6,
          }}
        >
          <div className="logo" style={{ width: 30, height: 30, borderRadius: 8 }}>
            <i className="ti ti-shield-lock" style={{ color: '#fff', fontSize: 18 }} />
          </div>
          <span style={{ fontSize: 20, fontWeight: 600 }}>Auvex</span>
        </div>
        <div className="sub" style={{ textAlign: 'center', fontSize: 13, marginBottom: 20 }}>
          Sign in to your console
        </div>

        {/* One-click sandbox — no key needed. */}
        <button
          className="btn-primary"
          onClick={tryDemo}
          disabled={demoBusy}
          style={{
            width: '100%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            padding: 11,
            fontSize: 13,
            fontWeight: 500,
          }}
        >
          <i className="ti ti-sparkles" />
          {demoBusy ? 'Loading demo…' : 'Try the live demo'}
        </button>
        <div className="muted" style={{ textAlign: 'center', fontSize: 11, margin: '6px 0 4px' }}>
          A sandbox org with sample data — no sign-up.
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '14px 0' }}>
          <div style={{ flex: 1, height: 1, background: 'var(--color-border-tertiary)' }} />
          <span className="muted" style={{ fontSize: 11 }}>
            or sign in
          </span>
          <div style={{ flex: 1, height: 1, background: 'var(--color-border-tertiary)' }} />
        </div>

        {providers.map((p) => (
          <button
            key={p.name}
            disabled
            title="SSO needs an OAuth client to be configured for this provider"
            style={{
              width: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 8,
              padding: 10,
              fontSize: 13,
              marginBottom: 8,
              textTransform: 'capitalize',
            }}
          >
            <i className={`ti ${PROVIDER_ICON[p.name] ?? 'ti-key'}`} />
            Continue with {p.name}
            <span className="badge" style={{ fontSize: 9, padding: '1px 6px' }}>
              soon
            </span>
          </button>
        ))}

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '12px 0' }}>
          <div style={{ flex: 1, height: 1, background: 'var(--color-border-tertiary)' }} />
          <span className="muted" style={{ fontSize: 11 }}>
            or use an API key
          </span>
          <div style={{ flex: 1, height: 1, background: 'var(--color-border-tertiary)' }} />
        </div>

        <form onSubmit={submit}>
          <label htmlFor="apikey">API key</label>
          <input
            id="apikey"
            type="password"
            placeholder="auvex_sk_…"
            value={key}
            onChange={(e) => setKey(e.target.value)}
            style={{ width: '100%', marginBottom: 12, fontFamily: 'var(--font-mono)' }}
          />
          {error && (
            <div
              className="badge badge-danger"
              style={{ display: 'block', textAlign: 'left', marginBottom: 12, padding: '8px 10px' }}
            >
              {error}
            </div>
          )}
          <button
            type="submit"
            disabled={busy || !key.trim()}
            style={{ width: '100%', padding: 11, fontSize: 13 }}
          >
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        <div className="muted" style={{ textAlign: 'center', fontSize: 11, marginTop: 14 }}>
          Your key is stored only in this browser.
        </div>
      </div>
    </div>
  )
}
