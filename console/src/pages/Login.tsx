import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { ApiError, api } from '../lib/api'
import type { SsoProvider } from '../lib/types'
import { Alert, Button, Card, Divider, TextField } from '../components/ui'

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
      <Card className="anim" style={{ width: 380, padding: '30px 28px' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 9,
            justifyContent: 'center',
            marginBottom: 6,
          }}
        >
          <div className="logo" style={{ width: 32, height: 32, borderRadius: 9 }}>
            <i className="ti ti-shield-lock" style={{ color: '#fff', fontSize: 19 }} />
          </div>
          <span style={{ fontSize: 21, fontWeight: 600 }}>Auvex</span>
        </div>
        <div className="sub" style={{ textAlign: 'center', fontSize: 13, marginBottom: 22 }}>
          Sign in to your console
        </div>

        {/* One-click sandbox — no key needed. */}
        <Button
          variant="primary"
          fullWidth
          icon="ti-sparkles"
          loading={demoBusy}
          onClick={tryDemo}
        >
          {demoBusy ? 'Loading demo…' : 'Try the live demo'}
        </Button>
        <div className="muted" style={{ textAlign: 'center', fontSize: 11, margin: '8px 0 2px' }}>
          A sandbox org with sample data — no sign-up.
        </div>

        {providers.length > 0 && (
          <>
            <Divider label="or sign in" />
            {providers.map((p) => (
              <div key={p.name} style={{ marginBottom: 8 }}>
                <Button
                  variant="secondary"
                  fullWidth
                  disabled
                  icon={PROVIDER_ICON[p.name] ?? 'ti-key'}
                  iconRight="ti-arrow-right"
                  style={{ textTransform: 'capitalize' }}
                >
                  Continue with {p.name}
                  <span className="badge" style={{ fontSize: 9, padding: '1px 6px', marginLeft: 4 }}>
                    soon
                  </span>
                </Button>
              </div>
            ))}
          </>
        )}

        <Divider label="or use an API key" />

        <form onSubmit={submit}>
          <TextField
            label="API key"
            type="password"
            icon="ti-key"
            placeholder="auvex_sk_…"
            value={key}
            onChange={setKey}
            fullWidth
          />
          {error && (
            <div style={{ margin: '12px 0' }}>
              <Alert tone="danger">{error}</Alert>
            </div>
          )}
          <div style={{ marginTop: error ? 0 : 12 }}>
            <Button
              type="submit"
              variant="primary"
              fullWidth
              loading={busy}
              disabled={!key.trim()}
            >
              {busy ? 'Signing in…' : 'Use API key'}
            </Button>
          </div>
        </form>
        <div className="muted" style={{ textAlign: 'center', fontSize: 11, marginTop: 16 }}>
          Your key is stored only in this browser.
        </div>
      </Card>
    </div>
  )
}
