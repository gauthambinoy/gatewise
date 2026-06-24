import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { useAuth } from '../auth/AuthContext'
import { useT } from '../lib/i18n'
import {
  Button,
  Card,
  CardHeader,
  Chip,
  Divider,
  ErrorState,
  Loading,
  PageHeader,
} from '../components/ui'

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
  const { t } = useT()
  const tr = t as (k: string) => string
  const { tenant, logout } = useAuth()
  const providers = useApi(() => api.providers(), [])

  return (
    <>
      <PageHeader
        title={tr('nav.settings')}
        subtitle="Providers, deployment and organization."
        actions={
          <Button variant="secondary" icon="ti-logout-2" onClick={logout}>
            {tr('common.signOut')}
          </Button>
        }
      />

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Card>
          <CardHeader icon="ti-building" title="Organization" />
          <Field label="Name">{tenant?.name ?? '—'}</Field>
          <Field label="Slug" mono>
            {tenant?.slug ?? '—'}
          </Field>
          <Field label="Tenant ID" mono muted last>
            {tenant?.id ?? '—'}
          </Field>
        </Card>

        <Card>
          <CardHeader
            icon="ti-lock"
            title="Single sign-on"
            subtitle="SSO sign-in requires an OAuth client id/secret and is wired up per provider."
          />
          {providers.loading ? (
            <Loading label={tr('common.loading')} />
          ) : providers.error || !providers.data ? (
            <ErrorState message={providers.error ?? 'No data'} onRetry={providers.reload} />
          ) : (
            providers.data.map((p, i) => (
              <div key={p.name}>
                {i > 0 && <Divider />}
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '11px 0',
                  }}
                >
                  <span style={{ flex: 1, fontSize: 13 }}>{cap(p.name)}</span>
                  {p.configured ? (
                    <Chip tone="success" icon="ti-circle-check">
                      Configured
                    </Chip>
                  ) : (
                    <Chip icon="ti-minus">Not configured</Chip>
                  )}
                </div>
              </div>
            ))
          )}
        </Card>

        <Card>
          <CardHeader
            icon="ti-cloud-cog"
            title="Deployment & data"
            subtitle="Self-host on AWS, Azure, GCP or on-prem. Audit logs never leave your region."
            actions={
              <span className="muted" style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 11 }}>
                <i className="ti ti-info-circle" style={{ fontSize: 14 }} />
                Configuration guidance
              </span>
            }
          />
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
        </Card>
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
