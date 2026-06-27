import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { useT } from '../lib/i18n'
import {
  Card,
  CardHeader,
  Chip,
  EmptyState,
  ErrorState,
  Loading,
  PageHeader,
  ProgressBar,
  StatCard,
  money,
  num,
} from '../components/ui'
import type { Tone } from '../components/ui'

/** A labeled horizontal bar — label on the left, value on the right, fill below. */
function Bar({
  label,
  value,
  max,
  capitalize = false,
  tone,
}: {
  label: string
  value: number
  max: number
  capitalize?: boolean
  tone?: Tone
}) {
  return (
    <div style={{ marginBottom: 10 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          fontSize: 12,
          marginBottom: 4,
        }}
      >
        <span
          className="sub"
          style={{
            ...(capitalize ? { textTransform: 'capitalize' } : null),
            ...(tone ? { color: `var(--color-text-${tone})` } : null),
          }}
        >
          {capitalize ? label.replace(/_/g, ' ') : label}
        </span>
        <span>{num(value)}</span>
      </div>
      <ProgressBar value={(value / max) * 100} height={6} tone={tone ?? 'info'} />
    </div>
  )
}

export function UsageCost() {
  const { t } = useT()
  const tr = t as (k: string) => string
  const usage = useApi(() => api.usage())

  if (usage.loading) return <Loading />
  if (usage.error || !usage.data)
    return <ErrorState message={usage.error ?? tr('common.noData')} onRetry={usage.reload} />

  const d = usage.data

  if (d.totalCalls === 0) {
    return (
      <>
        <PageHeader title={tr('nav.usage')} subtitle={tr('usage.subtitle')} />
        <EmptyState
          icon="ti-chart-bar"
          title={tr('usage.emptyTitle')}
          message={tr('usage.emptyMsg')}
        />
      </>
    )
  }

  const models = Object.entries(d.byModel).sort((a, b) => b[1] - a[1])
  const maxModel = Math.max(1, ...models.map(([, n]) => n))

  const redactions = Object.entries(d.redactionByType).sort((a, b) => b[1] - a[1])
  const maxRedaction = Math.max(1, ...redactions.map(([, n]) => n))

  const verdicts: { label: string; value: number; tone: Tone }[] = [
    { label: tr('verdict.allowed'), value: d.allowed, tone: 'success' },
    { label: tr('verdict.redacted'), value: d.redacted, tone: 'info' },
    { label: tr('verdict.blocked'), value: d.blocked, tone: 'danger' },
  ]
  const maxVerdict = Math.max(1, ...verdicts.map((v) => v.value))

  return (
    <>
      <PageHeader title={tr('nav.usage')} subtitle={tr('usage.subtitle')} />

      <div className="stat-grid" style={{ marginBottom: 16 }}>
        <StatCard label={tr('dash.totalCost')} value={money(d.totalCostUsd)} icon="ti-coin" />
        <StatCard label={tr('usage.requests')} value={num(d.totalCalls)} icon="ti-activity" />
        <StatCard label={tr('usage.totalTokens')} value={num(d.totalTokens)} icon="ti-hash" />
        <StatCard
          label={tr('usage.redacted')}
          value={num(d.redacted)}
          icon="ti-eye-off"
          tone="info"
        />
      </div>

      <Card style={{ marginBottom: 16 }}>
        <CardHeader title={tr('usage.byModel')} icon="ti-cpu" />
        {models.length > 0 ? (
          models.map(([model, n]) => <Bar key={model} label={model} value={n} max={maxModel} />)
        ) : (
          <div className="muted" style={{ fontSize: 12 }}>
            {tr('dash.noModels')}
          </div>
        )}
      </Card>

      <Card style={{ marginBottom: 16 }}>
        <CardHeader
          title={tr('usage.verdictMix')}
          icon="ti-gavel"
          actions={
            <div style={{ display: 'flex', gap: 'var(--sp-2)' }}>
              {verdicts.map((v) => (
                <Chip key={v.label} tone={v.tone} size="sm">
                  {num(v.value)}
                </Chip>
              ))}
            </div>
          }
        />
        {verdicts.map((v) => (
          <Bar key={v.label} label={v.label} value={v.value} max={maxVerdict} tone={v.tone} />
        ))}
      </Card>

      <Card>
        <CardHeader title={tr('usage.byType')} icon="ti-shield-lock" />
        {redactions.length > 0 ? (
          redactions.map(([type, n]) => (
            <Bar key={type} label={type} value={n} max={maxRedaction} capitalize />
          ))
        ) : (
          <div className="muted" style={{ fontSize: 12 }}>
            {tr('dash.noLeaks')}
          </div>
        )}
      </Card>
    </>
  )
}
