import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
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
        <span>{value.toLocaleString()}</span>
      </div>
      <ProgressBar value={(value / max) * 100} height={6} tone={tone ?? 'info'} />
    </div>
  )
}

export function UsageCost() {
  const usage = useApi(() => api.usage())

  if (usage.loading) return <Loading />
  if (usage.error || !usage.data)
    return <ErrorState message={usage.error ?? 'No data'} onRetry={usage.reload} />

  const d = usage.data

  if (d.totalCalls === 0) {
    return (
      <>
        <PageHeader title="Usage & cost" subtitle="Cost and traffic across the gateway" />
        <EmptyState
          icon="ti-chart-bar"
          title="No usage yet"
          message="Send some traffic through the gateway to see cost and usage here."
        />
      </>
    )
  }

  const models = Object.entries(d.byModel).sort((a, b) => b[1] - a[1])
  const maxModel = Math.max(1, ...models.map(([, n]) => n))

  const redactions = Object.entries(d.redactionByType).sort((a, b) => b[1] - a[1])
  const maxRedaction = Math.max(1, ...redactions.map(([, n]) => n))

  const verdicts: { label: string; value: number; tone: Tone }[] = [
    { label: 'Allowed', value: d.allowed, tone: 'success' },
    { label: 'Redacted', value: d.redacted, tone: 'info' },
    { label: 'Blocked', value: d.blocked, tone: 'danger' },
  ]
  const maxVerdict = Math.max(1, ...verdicts.map((v) => v.value))

  return (
    <>
      <PageHeader title="Usage & cost" subtitle="Cost and traffic across the gateway" />

      <div className="stat-grid" style={{ marginBottom: 16 }}>
        <StatCard label="Total cost" value={money(d.totalCostUsd)} icon="ti-coin" />
        <StatCard label="Requests" value={d.totalCalls.toLocaleString()} icon="ti-activity" />
        <StatCard label="Total tokens" value={d.totalTokens.toLocaleString()} icon="ti-hash" />
        <StatCard
          label="Redacted"
          value={d.redacted.toLocaleString()}
          icon="ti-eye-off"
          tone="info"
        />
      </div>

      <Card style={{ marginBottom: 16 }}>
        <CardHeader title="Requests by model" icon="ti-cpu" />
        {models.length > 0 ? (
          models.map(([model, n]) => <Bar key={model} label={model} value={n} max={maxModel} />)
        ) : (
          <div className="muted" style={{ fontSize: 12 }}>
            No model traffic yet.
          </div>
        )}
      </Card>

      <Card style={{ marginBottom: 16 }}>
        <CardHeader
          title="Verdict mix"
          icon="ti-gavel"
          actions={
            <div style={{ display: 'flex', gap: 'var(--sp-2)' }}>
              {verdicts.map((v) => (
                <Chip key={v.label} tone={v.tone} size="sm">
                  {v.value.toLocaleString()}
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
        <CardHeader title="Redactions by type" icon="ti-shield-lock" />
        {redactions.length > 0 ? (
          redactions.map(([type, n]) => (
            <Bar key={type} label={type} value={n} max={maxRedaction} capitalize />
          ))
        ) : (
          <div className="muted" style={{ fontSize: 12 }}>
            Nothing sensitive masked yet.
          </div>
        )}
      </Card>
    </>
  )
}
