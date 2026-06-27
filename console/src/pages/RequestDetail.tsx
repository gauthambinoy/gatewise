import { Link, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { useT } from '../lib/i18n'
import {
  Alert,
  Badge,
  Card,
  CardHeader,
  ErrorState,
  Loading,
  StatCard,
  dt,
  money,
  num,
  verdictTone,
} from '../components/ui'

export function RequestDetail() {
  const { id } = useParams()
  const { t } = useT()
  const tr = t as (k: string, vars?: Record<string, string | number>) => string
  const entry = useApi(() => api.auditEntry(id as string), [id])
  const verify = useApi(() => api.verify(), [])

  if (entry.loading) return <Loading />
  if (entry.error || !entry.data)
    return <ErrorState message={entry.error ?? tr('req.notFound')} onRetry={entry.reload} />

  const e = entry.data
  const counts = e.redactionCounts ?? {}
  const items = Object.values(counts).reduce((a, b) => a + b, 0)
  const tokens = (e.promptTokens ?? 0) + (e.completionTokens ?? 0)
  const intact = verify.data?.intact ?? true

  return (
    <Card>
      <Link
        to="/audit"
        className="muted"
        style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 12, marginBottom: 12 }}
      >
        <i className="ti ti-arrow-left" aria-hidden /> {tr('req.back')}
      </Link>

      <CardHeader
        title={
          <span className="mono" style={{ fontWeight: 500 }}>
            {tr('req.title', { id: e.requestId.slice(0, 8) })}
          </span>
        }
        subtitle={`${e.actor} · ${e.model} · ${dt(e.createdAt)}`}
        actions={<Badge tone={verdictTone(e.verdict)}>{e.verdict}</Badge>}
      />

      <div style={{ fontSize: 13, fontWeight: 500, marginBottom: 6 }}>{tr('req.promptHeading')}</div>
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
        {e.promptRedacted || <span className="muted">{tr('req.noPrompt')}</span>}
      </div>

      <div className="stat-grid" style={{ marginBottom: 18 }}>
        <StatCard
          label={tr('req.itemsRedacted')}
          value={items}
          icon="ti-eraser"
          tone={items ? 'info' : undefined}
        />
        <StatCard label={tr('req.tokens')} value={tokens ? num(tokens) : '—'} icon="ti-coin" />
        <StatCard label={tr('req.cost')} value={money(e.costUsd)} icon="ti-currency-dollar" />
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

      <Alert
        tone={intact ? 'success' : 'danger'}
        icon={intact ? 'ti-lock-check' : 'ti-lock-open'}
        title={intact ? tr('req.chainVerified') : tr('req.chainFailed')}
      >
        {intact ? tr('req.intactMsg') : tr('req.failedMsg')}
      </Alert>
      <div className="muted mono" style={{ fontSize: 11, marginTop: 8, wordBreak: 'break-all' }}>
        {tr('req.entryLabel')} {e.entryHash}
      </div>
    </Card>
  )
}
