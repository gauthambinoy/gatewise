import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { useT } from '../lib/i18n'
import {
  Card,
  CardHeader,
  EmptyState,
  ErrorState,
  Loading,
  PageHeader,
  ProgressBar,
  StatCard,
  money,
} from '../components/ui'

/** A labelled cost bar — name on the left, a EUR-localised amount on the right, fill below. */
function CostBar({ label, value, max }: { label: string; value: number; max: number }) {
  return (
    <div style={{ marginBottom: 10 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 4, gap: 12 }}>
        <span
          className="sub"
          style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
        >
          {label}
        </span>
        <span style={{ flexShrink: 0 }}>{money(value)}</span>
      </div>
      <ProgressBar value={max > 0 ? (value / max) * 100 : 0} height={6} gradient />
    </div>
  )
}

export function Chargeback() {
  const { t } = useT()
  const tr = t as (k: string) => string
  const report = useApi(() => api.chargeback(), [])

  if (report.loading) return <Loading />
  if (report.error || !report.data)
    return <ErrorState message={report.error ?? tr('common.noData')} onRetry={report.reload} />

  const d = report.data

  if (d.totalCostUsd === 0) {
    return (
      <>
        <PageHeader title={tr('nav.chargeback')} subtitle={tr('charge.subtitle')} />
        <EmptyState
          icon="ti-receipt"
          title={tr('charge.emptyTitle')}
          message={tr('charge.emptyMsg')}
        />
      </>
    )
  }

  const models = Object.entries(d.costByModel).sort((a, b) => b[1] - a[1])
  const maxModel = Math.max(0, ...models.map(([, n]) => n))

  const users = [...d.costByUser].sort((a, b) => b.costUsd - a.costUsd)
  const maxUser = Math.max(0, ...users.map((u) => u.costUsd))

  return (
    <>
      <PageHeader title={tr('nav.chargeback')} subtitle={tr('charge.subtitle')} />

      <div className="stat-grid" style={{ marginBottom: 16 }}>
        <StatCard label={tr('dash.totalCost')} value={money(d.totalCostUsd)} icon="ti-coin" />
        <StatCard
          label={tr('charge.last30')}
          value={money(d.last30DaysCostUsd)}
          icon="ti-calendar"
        />
        <StatCard
          label={tr('charge.projected')}
          value={money(d.projectedMonthlyCostUsd)}
          icon="ti-trending-up"
          tone="info"
        />
      </div>

      <Card style={{ marginBottom: 16 }}>
        <CardHeader title={tr('charge.byModel')} icon="ti-cpu" />
        {models.length > 0 ? (
          models.map(([model, n]) => <CostBar key={model} label={model} value={n} max={maxModel} />)
        ) : (
          <div className="muted" style={{ fontSize: 12 }}>
            {tr('dash.noModels')}
          </div>
        )}
      </Card>

      <Card>
        <CardHeader title={tr('charge.byUser')} icon="ti-users" />
        {users.length > 0 ? (
          users.map((u) => <CostBar key={u.actor} label={u.actor} value={u.costUsd} max={maxUser} />)
        ) : (
          <div className="muted" style={{ fontSize: 12 }}>
            {tr('users.emptyTitle')}
          </div>
        )}
      </Card>
    </>
  )
}
