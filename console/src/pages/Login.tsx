import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { ApiError, api } from '../lib/api'
import type { SsoProvider } from '../lib/types'

const PROVIDER_ICON: Record<string, string> = { google: 'ti-brand-google', okta: 'ti-key' }

export function Login() {
  const { login } = useAuth()
  const [key, setKey] = useState('')
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const [providers, setProviders] = useState<SsoProvider[]>([])

  useEffect(() => {
    api.providers().then(setProviders).catch(() => setProviders([]))
  }, [])

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(undefined)
    setBusy(true)
    try {
      await login(key)
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 401
          ? 'That API key was not recognized.'
          : err instanceof Error
            ? err.message
            : 'Sign-in failed.',
      )
    } finally {
      setBusy(false)
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
        <div
          className="sub"
          style={{ textAlign: 'center', fontSize: 13, marginBottom: 22 }}
        >
          Sign in to your console
        </div>

        {providers.map((p) => (
          <button
            key={p.name}
            disabled
            title={p.configured ? 'SSO sign-in is being wired up' : 'Configure this provider to enable SSO'}
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
          </button>
        ))}

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '16px 0' }}>
          <div style={{ flex: 1, height: 1, background: 'var(--color-border-tertiary)' }} />
          <span className="muted" style={{ fontSize: 11 }}>
            use an API key
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
            autoFocus
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
            className="btn-primary"
            disabled={busy || !key.trim()}
            style={{ width: '100%', padding: 11, fontSize: 13, fontWeight: 500 }}
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
