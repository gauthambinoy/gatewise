import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { EmptyState, ErrorState, Loading, PageHeader, Stat, money } from '../components/ui'
import type { Tone } from '../components/ui'

/** A labeled horizontal bar — label on the left, value on the right, fill below. */
function Bar({
  label,
  value,
  max,
  capitalize = false,
}: {
  label: string
  value: number
  max: number
  capitalize?: boolean
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
          style={capitalize ? { textTransform: 'capitalize' } : undefined}
        >
          {capitalize ? label.replace(/_/g, ' ') : label}
        </span>
        <span>{value.toLocaleString()}</span>
      </div>
      <div style={{ height: 6, background: 'var(--color-background-tertiary)', borderRadius: 4 }}>
        <div
          style={{
            width: `${(value / max) * 100}%`,
            height: 6,
            background: 'var(--color-text-info)',
            borderRadius: 4,
          }}
        />
      </div>
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
        <Stat label="Total cost" value={money(d.totalCostUsd)} />
        <Stat label="Requests" value={d.totalCalls.toLocaleString()} />
        <Stat label="Total tokens" value={d.totalTokens.toLocaleString()} />
        <Stat label="Redacted" value={d.redacted.toLocaleString()} tone="info" />
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 12 }}>Requests by model</div>
        {models.length > 0 ? (
          models.map(([model, n]) => <Bar key={model} label={model} value={n} max={maxModel} />)
        ) : (
          <div className="muted" style={{ fontSize: 12 }}>
            No model traffic yet.
          </div>
        )}
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 12 }}>Verdict mix</div>
        {verdicts.map((v) => (
          <div key={v.label} style={{ marginBottom: 10 }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                fontSize: 12,
                marginBottom: 4,
              }}
            >
              <span className="sub" style={{ color: `var(--color-text-${v.tone})` }}>
                {v.label}
              </span>
              <span>{v.value.toLocaleString()}</span>
            </div>
            <div
              style={{ height: 6, background: 'var(--color-background-tertiary)', borderRadius: 4 }}
            >
              <div
                style={{
                  width: `${(v.value / maxVerdict) * 100}%`,
                  height: 6,
                  background: `var(--color-text-${v.tone})`,
                  borderRadius: 4,
                }}
              />
            </div>
          </div>
        ))}
      </div>

      <div className="card">
        <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 12 }}>Redactions by type</div>
        {redactions.length > 0 ? (
          redactions.map(([type, n]) => (
            <Bar key={type} label={type} value={n} max={maxRedaction} capitalize />
          ))
        ) : (
          <div className="muted" style={{ fontSize: 12 }}>
            Nothing sensitive masked yet.
          </div>
        )}
      </div>
    </>
  )
}
