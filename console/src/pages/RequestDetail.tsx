import { Link, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { Badge, ErrorState, Loading, Stat, dt, money, verdictTone } from '../components/ui'

export function RequestDetail() {
  const { id } = useParams()
  const entry = useApi(() => api.auditEntry(id as string), [id])
  const verify = useApi(() => api.verify(), [])

  if (entry.loading) return <Loading />
  if (entry.error || !entry.data)
    return <ErrorState message={entry.error ?? 'Not found'} onRetry={entry.reload} />

  const e = entry.data
  const counts = e.redactionCounts ?? {}
  const items = Object.values(counts).reduce((a, b) => a + b, 0)
  const tokens = (e.promptTokens ?? 0) + (e.completionTokens ?? 0)
  const intact = verify.data?.intact ?? true

  return (
    <div className="card">
      <Link
        to="/audit"
        className="muted"
        style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 12, marginBottom: 12 }}
      >
        <i className="ti ti-arrow-left" /> Back to audit log
      </Link>

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
        <div className="mono" style={{ fontSize: 16, fontWeight: 500 }}>
          Request {e.requestId.slice(0, 8)}…
        </div>
        <Badge tone={verdictTone(e.verdict)}>{e.verdict}</Badge>
      </div>
      <div className="muted" style={{ fontSize: 12, marginBottom: 18 }}>
        {e.actor} · {e.model} · {dt(e.createdAt)}
      </div>

      <div style={{ fontSize: 13, fontWeight: 500, marginBottom: 6 }}>Prompt sent to the model</div>
      <div
        style={{
          background: 'var(--color-background-secondary)',
          borderRadius: 'var(--border-radius-md)',
          padding: '12px 14px',
          fontSize: 13,
          lineHeight: 1.7,
          marginBottom: 18,
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
        }}
      >
        {e.promptRedacted || <span className="muted">(no prompt text captured)</span>}
      </div>

      <div className="stat-grid" style={{ marginBottom: 18 }}>
        <Stat label="Items redacted" value={items} tone={items ? 'info' : undefined} />
        <Stat label="Tokens" value={tokens ? tokens.toLocaleString() : '—'} />
        <Stat label="Cost" value={money(e.costUsd)} />
      </div>

      {items > 0 && (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 18 }}>
          {Object.entries(counts).map(([type, n]) => (
            <Badge key={type} tone="info">
              {n}× {type.replace(/_/g, ' ')}
            </Badge>
          ))}
        </div>
      )}

      <div
        className="hint"
        style={{
          background: intact ? 'var(--color-background-success)' : 'var(--color-background-danger)',
          color: intact ? 'var(--color-text-success)' : 'var(--color-text-danger)',
        }}
      >
        <i className={`ti ${intact ? 'ti-lock-check' : 'ti-lock-open'}`} style={{ fontSize: 17 }} />
        {intact
          ? 'Hash-chain verified — this record has not been altered.'
          : 'Chain verification failed for this tenant.'}
      </div>
      <div className="muted mono" style={{ fontSize: 11, marginTop: 8, wordBreak: 'break-all' }}>
        entry: {e.entryHash}
      </div>
    </div>
  )
}
