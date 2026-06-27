import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { ApiError, api } from '../lib/api'
import { useT } from '../lib/i18n'
import type { SsoProvider } from '../lib/types'
import { Alert, Button, Card, Divider, TextField } from '../components/ui'

const PROVIDER_ICON: Record<string, string> = { google: 'ti-brand-google', okta: 'ti-key' }
const DEMO_KEY = 'auvex_demo_key'

export function Login() {
  const { login } = useAuth()
  const { t } = useT()
  const tr = t as (k: string, vars?: Record<string, string | number>) => string
  const [key, setKey] = useState('')
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const [demoBusy, setDemoBusy] = useState(false)
  const [providers, setProviders] = useState<SsoProvider[]>([])

  useEffect(() => {
    api.providers().then(setProviders).catch(() => setProviders([]))
  }, [])

  function messageFor(err: unknown, fallback: string): string {
    if (err instanceof ApiError && err.status === 401) return tr('login.errBadKey')
    return err instanceof Error ? err.message : fallback
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(undefined)
    setBusy(true)
    try {
      await login(key)
    } catch (err) {
      setError(messageFor(err, tr('login.errFailed')))
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
      setError(tr('login.errDemo'))
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
            <i className="ti ti-shield-lock" style={{ color: '#fff', fontSize: 19 }} aria-hidden />
          </div>
          <span style={{ fontSize: 21, fontWeight: 600 }}>Auvex</span>
        </div>
        <div className="sub" style={{ textAlign: 'center', fontSize: 13, marginBottom: 22 }}>
          {tr('login.subtitle')}
        </div>

        {/* One-click sandbox — no key needed. */}
        <Button
          variant="primary"
          fullWidth
          icon="ti-sparkles"
          loading={demoBusy}
          onClick={tryDemo}
        >
          {demoBusy ? tr('login.loadingDemo') : tr('login.tryDemo')}
        </Button>
        <div className="muted" style={{ textAlign: 'center', fontSize: 11, margin: '8px 0 2px' }}>
          {tr('login.demoNote')}
        </div>

        {providers.length > 0 && (
          <>
            <Divider label={tr('login.orSignIn')} />
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
                  {tr('login.continueWith', { name: p.name })}
                  <span className="badge" style={{ fontSize: 9, padding: '1px 6px', marginLeft: 4 }}>
                    {tr('login.soon')}
                  </span>
                </Button>
              </div>
            ))}
          </>
        )}

        <Divider label={tr('login.orApiKey')} />

        <form onSubmit={submit}>
          <TextField
            label={tr('login.apiKeyLabel')}
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
              {busy ? tr('login.signingIn') : tr('login.useApiKey')}
            </Button>
          </div>
        </form>
        <div className="muted" style={{ textAlign: 'center', fontSize: 11, marginTop: 16 }}>
          {tr('login.keyStored')}
        </div>
      </Card>
    </div>
  )
}
