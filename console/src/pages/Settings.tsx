import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { useAuth } from '../auth/AuthContext'
import { Badge, ErrorState, Loading } from '../components/ui'

const DEPLOY_TILES = [
  { icon: 'ti-brand-aws', label: 'AWS' },
  { icon: 'ti-brand-azure', label: 'Azure' },
  { icon: 'ti-brand-google', label: 'GCP' },
  { icon: 'ti-server', label: 'On-prem' },
]

function cap(s: string): string {
  return s ? s[0].toUpperCase() + s.slice(1) : s
}

export function Settings() {
  const { tenant, logout } = useAuth()
  const providers = useApi(() => api.providers(), [])

  return (
    <>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 18,
        }}
      >
        <div>
          <div className="page-title">Settings</div>
          <div className="muted" style={{ fontSize: 12 }}>
            Providers, deployment and organization.
          </div>
        </div>
        <button
          onClick={logout}
          style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, padding: '8px 14px' }}
        >
          <i className="ti ti-logout-2" style={{ fontSize: 16 }} />
          Sign out
        </button>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <div className="card">
          <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 12 }}>Organization</div>
          <Field label="Name">{tenant?.name ?? '—'}</Field>
          <Field label="Slug" mono>
            {tenant?.slug ?? '—'}
          </Field>
          <Field label="Tenant ID" mono muted last>
            {tenant?.id ?? '—'}
          </Field>
        </div>

        <div className="card">
          <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>Single sign-on</div>
          <div className="muted" style={{ fontSize: 12, marginBottom: 14 }}>
            SSO sign-in requires an OAuth client id/secret and is wired up per provider.
          </div>
          {providers.loading ? (
            <Loading />
          ) : providers.error || !providers.data ? (
            <ErrorState message={providers.error ?? 'No data'} onRetry={providers.reload} />
          ) : (
            providers.data.map((p) => (
              <div
                key={p.name}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: '11px 0',
                  borderTop: '0.5px solid var(--color-border-tertiary)',
                }}
              >
                <span style={{ flex: 1, fontSize: 13 }}>{cap(p.name)}</span>
                {p.configured ? (
                  <Badge tone="success">Configured</Badge>
                ) : (
                  <span className="badge muted" style={{ background: 'var(--color-background-secondary)' }}>
                    Not configured
                  </span>
                )}
              </div>
            ))
          )}
        </div>

        <div className="card">
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 4,
            }}
          >
            <div style={{ fontSize: 14, fontWeight: 600 }}>Deployment &amp; data</div>
            <span className="muted" style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 11 }}>
              <i className="ti ti-info-circle" style={{ fontSize: 14 }} />
              Configuration guidance
            </span>
          </div>
          <div className="muted" style={{ fontSize: 12, marginBottom: 14 }}>
            Self-host on AWS, Azure, GCP or on-prem. Audit logs never leave your region.
          </div>
          <div style={{ display: 'flex', gap: 10 }}>
            {DEPLOY_TILES.map((t) => (
              <div
                key={t.label}
                style={{
                  flex: 1,
                  border: '0.5px solid var(--color-border-secondary)',
                  borderRadius: 'var(--border-radius-md)',
                  padding: 12,
                  textAlign: 'center',
                }}
              >
                <i className={`ti ${t.icon}`} style={{ fontSize: 20, color: 'var(--color-text-secondary)' }} />
                <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginTop: 4 }}>
                  {t.label}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </>
  )
}

function Field({
  label,
  children,
  mono,
  muted,
  last,
}: {
  label: string
  children: React.ReactNode
  mono?: boolean
  muted?: boolean
  last?: boolean
}) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'baseline',
        gap: 12,
        padding: '8px 0',
        borderBottom: last ? undefined : '0.5px solid var(--color-border-tertiary)',
      }}
    >
      <span className="sub" style={{ fontSize: 12, width: 90, flexShrink: 0 }}>
        {label}
      </span>
      <span
        className={`${mono ? 'mono ' : ''}${muted ? 'muted' : ''}`.trim()}
        style={{ fontSize: 13, wordBreak: 'break-all' }}
      >
        {children}
      </span>
    </div>
  )
}
