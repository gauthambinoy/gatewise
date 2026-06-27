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

// `name` is a brand literal shown as-is; `label` (when set) is an i18n key used instead.
const DEPLOY_TILES: { key: string; icon: string; name?: string; label?: string }[] = [
  { key: 'aws', icon: 'ti-brand-aws', name: 'AWS' },
  { key: 'azure', icon: 'ti-brand-azure', name: 'Azure' },
  { key: 'gcp', icon: 'ti-brand-google', name: 'GCP' },
  { key: 'onprem', icon: 'ti-server', label: 'settings.onPrem' },
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
        subtitle={tr('settings.subtitle')}
        actions={
          <Button variant="secondary" icon="ti-logout-2" onClick={logout}>
            {tr('common.signOut')}
          </Button>
        }
      />

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Card>
          <CardHeader icon="ti-building" title={tr('settings.org')} />
          <Field label={tr('settings.name')}>{tenant?.name ?? '—'}</Field>
          <Field label={tr('settings.slug')} mono>
            {tenant?.slug ?? '—'}
          </Field>
          <Field label={tr('settings.tenantId')} mono muted last>
            {tenant?.id ?? '—'}
          </Field>
        </Card>

        <Card>
          <CardHeader
            icon="ti-lock"
            title={tr('settings.sso')}
            subtitle={tr('settings.ssoSubtitle')}
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
                      {tr('settings.configured')}
                    </Chip>
                  ) : (
                    <Chip icon="ti-minus">{tr('settings.notConfigured')}</Chip>
                  )}
                </div>
              </div>
            ))
          )}
        </Card>

        <Card>
          <CardHeader
            icon="ti-cloud-cog"
            title={tr('settings.deploy')}
            subtitle={tr('settings.deploySubtitle')}
            actions={
              <span className="muted" style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 11 }}>
                <i className="ti ti-info-circle" style={{ fontSize: 14 }} aria-hidden />
                {tr('settings.configGuidance')}
              </span>
            }
          />
          <div style={{ display: 'flex', gap: 10 }}>
            {DEPLOY_TILES.map((tile) => (
              <div
                key={tile.key}
                style={{
                  flex: 1,
                  border: '0.5px solid var(--color-border-secondary)',
                  borderRadius: 'var(--border-radius-md)',
                  padding: 12,
                  textAlign: 'center',
                }}
              >
                <i
                  className={`ti ${tile.icon}`}
                  style={{ fontSize: 20, color: 'var(--color-text-secondary)' }}
                  aria-hidden
                />
                <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginTop: 4 }}>
                  {tile.label ? tr(tile.label) : tile.name}
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
