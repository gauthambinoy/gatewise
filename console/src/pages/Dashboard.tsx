import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { useT } from '../lib/i18n'
import {
  Badge,
  Card,
  CardHeader,
  CountUp,
  Chip,
  Divider,
  ErrorState,
  Loading,
  ProgressBar,
  StatCard,
  clock,
  money,
  num,
  verdictTone,
} from '../components/ui'

export function Dashboard() {
  const { t } = useT()
  const tr = t as (k: string) => string

  const usage = useApi(() => api.usage())
  const recent = useApi(() => api.audit({ size: 6 }))

  if (usage.loading) return <Loading />
  if (usage.error || !usage.data)
    return <ErrorState message={usage.error ?? 'No data'} onRetry={usage.reload} />

  const d = usage.data
  const leaks = Object.entries(d.redactionByType).sort((a, b) => b[1] - a[1])
  const models = Object.entries(d.byModel).sort((a, b) => b[1] - a[1])
  const maxLeak = Math.max(1, ...leaks.map(([, n]) => n))
  const totalModel = Math.max(1, ...[models.reduce((s, [, n]) => s + n, 0)])

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
          <div style={{ fontSize: 18, fontWeight: 600 }}>{tr('dash.overview')}</div>
          <div className="muted" style={{ fontSize: 12 }}>
            {tr('dash.subtitle')}
          </div>
        </div>
        <Chip tone="success" icon="ti-point-filled">
          {tr('dash.healthy')}
        </Chip>
      </div>

      <div className="stat-grid" style={{ marginBottom: 16 }}>
        <StatCard
          label={tr('dash.totalRequests')}
          value={<CountUp end={d.totalCalls} />}
          icon="ti-activity"
        />
        <StatCard
          label={tr('dash.piiRedacted')}
          value={<CountUp end={d.redacted} />}
          icon="ti-eye-off"
          tone="info"
        />
        <StatCard
          label={tr('dash.blocked')}
          value={<CountUp end={d.blocked} />}
          icon="ti-ban"
          tone="danger"
        />
        <StatCard
          label={tr('dash.totalCost')}
          value={<CountUp end={d.totalCostUsd} format={money} />}
          icon="ti-coin"
        />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr', gap: 16 }}>
        <Card padding="14px 16px">
          <CardHeader
            title={tr('dash.recent')}
            icon="ti-clock"
            actions={
              <Link to="/audit" className="muted" style={{ fontSize: 12 }}>
                {tr('dash.viewAll')} →
              </Link>
            }
          />
          {recent.data && recent.data.entries.length > 0 ? (
            recent.data.entries.map((e, i) => (
              <Link
                key={e.id}
                to={`/audit/${e.id}`}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  padding: '9px 0',
                  borderTop: i === 0 ? undefined : '0.5px solid var(--color-border-tertiary)',
                }}
              >
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 13 }}>{e.model}</div>
                  <div className="muted" style={{ fontSize: 11 }}>
                    {e.actor} · {clock(e.createdAt)}
                  </div>
                </div>
                <Badge tone={verdictTone(e.verdict)}>{e.verdict}</Badge>
              </Link>
            ))
          ) : (
            <div className="muted" style={{ fontSize: 13, padding: '14px 0' }}>
              {tr('dash.noRequests')}
            </div>
          )}
        </Card>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Card padding="14px 16px">
            <CardHeader title={tr('dash.leaks')} icon="ti-shield-lock" />
            {leaks.length > 0 ? (
              leaks.map(([type, n]) => (
                <div key={type} style={{ marginBottom: 10 }}>
                  <div
                    style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 4 }}
                  >
                    <span className="sub" style={{ textTransform: 'capitalize' }}>
                      {type.replace(/_/g, ' ')}
                    </span>
                    <span>{num(n)}</span>
                  </div>
                  <ProgressBar value={(n / maxLeak) * 100} height={6} gradient />
                </div>
              ))
            ) : (
              <div className="muted" style={{ fontSize: 12 }}>
                {tr('dash.noLeaks')}
              </div>
            )}
          </Card>

          <Card padding="14px 16px">
            <CardHeader title={tr('dash.topModels')} icon="ti-cpu" />
            {models.length > 0 ? (
              models.slice(0, 5).map(([model, n], i) => (
                <div key={model}>
                  {i > 0 && <Divider />}
                  <div
                    style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, padding: '5px 0' }}
                  >
                    <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {model}
                    </span>
                    <span className="sub">{Math.round((n / totalModel) * 100)}%</span>
                  </div>
                </div>
              ))
            ) : (
              <div className="muted" style={{ fontSize: 12 }}>
                {tr('dash.noModels')}
              </div>
            )}
          </Card>
        </div>
      </div>
    </>
  )
}
