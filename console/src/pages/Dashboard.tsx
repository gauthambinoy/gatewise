import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { Badge, ErrorState, Loading, Stat, clock, money, verdictTone } from '../components/ui'

export function Dashboard() {
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
          <div style={{ fontSize: 18, fontWeight: 600 }}>Overview</div>
          <div className="muted" style={{ fontSize: 12 }}>
            Live traffic across the gateway
          </div>
        </div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            fontSize: 12,
            color: 'var(--color-text-success)',
          }}
        >
          <span
            style={{
              width: 8,
              height: 8,
              borderRadius: '50%',
              background: 'var(--color-text-success)',
            }}
          />
          Gateway healthy
        </div>
      </div>

      <div className="stat-grid" style={{ marginBottom: 16 }}>
        <Stat label="Total requests" value={d.totalCalls.toLocaleString()} />
        <Stat label="PII redacted" value={d.redacted.toLocaleString()} tone="info" />
        <Stat label="Blocked" value={d.blocked.toLocaleString()} tone="danger" />
        <Stat label="Total cost" value={money(d.totalCostUsd)} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr', gap: 16 }}>
        <div className="card" style={{ padding: '14px 16px' }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 4,
            }}
          >
            <span style={{ fontSize: 14, fontWeight: 600 }}>Recent requests</span>
            <Link to="/audit" className="muted" style={{ fontSize: 12 }}>
              View all →
            </Link>
          </div>
          {recent.data && recent.data.entries.length > 0 ? (
            recent.data.entries.map((e) => (
              <Link
                key={e.id}
                to={`/audit/${e.id}`}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  padding: '9px 0',
                  borderTop: '0.5px solid var(--color-border-tertiary)',
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
              No requests yet — point an app at the gateway to see traffic here.
            </div>
          )}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div className="card" style={{ padding: '14px 16px' }}>
            <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 12 }}>Leaks prevented</div>
            {leaks.length > 0 ? (
              leaks.map(([type, n]) => (
                <div key={type} style={{ marginBottom: 10 }}>
                  <div
                    style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 4 }}
                  >
                    <span className="sub" style={{ textTransform: 'capitalize' }}>
                      {type.replace(/_/g, ' ')}
                    </span>
                    <span>{n.toLocaleString()}</span>
                  </div>
                  <div
                    style={{ height: 6, background: 'var(--color-background-tertiary)', borderRadius: 4 }}
                  >
                    <div
                      style={{
                        width: `${(n / maxLeak) * 100}%`,
                        height: 6,
                        background: 'var(--color-text-info)',
                        borderRadius: 4,
                      }}
                    />
                  </div>
                </div>
              ))
            ) : (
              <div className="muted" style={{ fontSize: 12 }}>
                Nothing sensitive masked yet.
              </div>
            )}
          </div>

          <div className="card" style={{ padding: '14px 16px' }}>
            <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 10 }}>Top models</div>
            {models.length > 0 ? (
              models.slice(0, 5).map(([model, n]) => (
                <div
                  key={model}
                  style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, padding: '5px 0' }}
                >
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {model}
                  </span>
                  <span className="sub">{Math.round((n / totalModel) * 100)}%</span>
                </div>
              ))
            ) : (
              <div className="muted" style={{ fontSize: 12 }}>
                No model traffic yet.
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  )
}
