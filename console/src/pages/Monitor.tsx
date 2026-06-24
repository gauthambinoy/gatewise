import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import type { AuditEntry, UsageSummary } from '../lib/types'
import { Badge, Button, Card, CardHeader, Select, clock, verdictTone } from '../components/ui'

const POLL_MS = 3000

export function Monitor() {
  const [entries, setEntries] = useState<AuditEntry[]>([])
  const [usage, setUsage] = useState<UsageSummary | null>(null)
  const [live, setLive] = useState(true)
  const [verdict, setVerdict] = useState('')
  const [freshIds, setFreshIds] = useState<Set<number>>(new Set())
  const seen = useRef<Set<number>>(new Set())

  useEffect(() => {
    let active = true
    async function poll() {
      try {
        const [page, u] = await Promise.all([
          api.audit({ verdict: verdict || undefined, size: 30 }),
          api.usage(),
        ])
        if (!active) return
        setUsage(u)
        const fresh = page.entries.filter((e) => !seen.current.has(e.id)).map((e) => e.id)
        if (seen.current.size > 0 && fresh.length) {
          setFreshIds(new Set(fresh))
          window.setTimeout(() => active && setFreshIds(new Set()), 1600)
        }
        page.entries.forEach((e) => seen.current.add(e.id))
        setEntries(page.entries)
      } catch {
        /* transient — keep the last good view */
      }
    }
    void poll()
    const id = live ? window.setInterval(poll, POLL_MS) : undefined
    return () => {
      active = false
      if (id) window.clearInterval(id)
    }
  }, [live, verdict])

  return (
    <>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 18,
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ fontSize: 22, fontWeight: 700, letterSpacing: '-0.02em' }}>Live monitoring</span>
            <span
              className="badge"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                background: live ? 'var(--color-background-success)' : 'var(--color-background-secondary)',
                color: live ? 'var(--color-text-success)' : 'var(--color-text-tertiary)',
                fontWeight: 600,
              }}
            >
              <span
                style={{
                  width: 7,
                  height: 7,
                  borderRadius: '50%',
                  background: live ? 'var(--color-text-success)' : 'var(--color-text-tertiary)',
                  boxShadow: live ? '0 0 0 0 rgba(52,211,153,0.6)' : 'none',
                  animation: live ? 'dotPulseLive 1.6s ease-in-out infinite' : 'none',
                }}
              />
              {live ? 'LIVE' : 'PAUSED'}
            </span>
          </div>
          <div className="muted" style={{ fontSize: 13, marginTop: 2 }}>
            Every AI call across the gateway, streaming in — sensitive data already masked.
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <Select
            value={verdict}
            onChange={setVerdict}
            options={[
              { value: '', label: 'All verdicts' },
              { value: 'allowed', label: 'Allowed' },
              { value: 'redacted', label: 'Redacted' },
              { value: 'blocked', label: 'Blocked' },
            ]}
          />
          <Button
            variant={live ? 'secondary' : 'primary'}
            icon={live ? 'ti-player-pause' : 'ti-player-play'}
            onClick={() => setLive((v) => !v)}
          >
            {live ? 'Pause' : 'Resume'}
          </Button>
        </div>
      </div>

      {usage && (
        <div className="stat-grid" style={{ marginBottom: 16 }}>
          <MiniStat label="Total calls" value={usage.totalCalls} icon="ti-activity" />
          <MiniStat label="Allowed" value={usage.allowed} icon="ti-circle-check" tone="success" />
          <MiniStat label="Redacted" value={usage.redacted} icon="ti-eye-off" tone="info" />
          <MiniStat label="Blocked" value={usage.blocked} icon="ti-ban" tone="danger" />
        </div>
      )}

      <Card padding="8px 0">
        <CardHeader
          title="Request feed"
          subtitle={`Refreshing every ${POLL_MS / 1000}s`}
          icon="ti-radar-2"
          actions={<span className="muted" style={{ fontSize: 12 }}>{entries.length} shown</span>}
        />
        <div>
          {entries.length === 0 ? (
            <div className="muted" style={{ fontSize: 13, padding: '24px 18px', textAlign: 'center' }}>
              Waiting for traffic… point an app at the gateway (see <Link to="/connect" style={{ color: 'var(--color-text-info)' }}>Connect</Link>).
            </div>
          ) : (
            entries.map((e) => {
              const isFresh = freshIds.has(e.id)
              return (
                <Link
                  key={e.id}
                  to={`/audit/${e.id}`}
                  style={{
                    display: 'grid',
                    gridTemplateColumns: '74px 1fr 150px 88px',
                    gap: 10,
                    alignItems: 'center',
                    padding: '11px 18px',
                    borderTop: '1px solid var(--color-border-tertiary)',
                    transition: 'background 0.8s var(--ease-out)',
                    background: isFresh ? 'var(--color-background-info)' : 'transparent',
                  }}
                >
                  <span className="muted mono" style={{ fontSize: 12 }}>
                    {clock(e.createdAt)}
                  </span>
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 13 }}>
                    <span className="sub">{e.actor}</span>
                    {e.promptRedacted ? ` · "${e.promptRedacted.slice(0, 56)}"` : ''}
                  </span>
                  <span className="sub mono" style={{ fontSize: 12, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {e.model}
                  </span>
                  <Badge tone={verdictTone(e.verdict)}>{e.verdict}</Badge>
                </Link>
              )
            })
          )}
        </div>
      </Card>
    </>
  )
}

function MiniStat({
  label,
  value,
  icon,
  tone,
}: {
  label: string
  value: number
  icon: string
  tone?: 'info' | 'success' | 'danger'
}) {
  return (
    <div className="card" style={{ padding: '14px 16px', display: 'flex', alignItems: 'center', gap: 12 }}>
      <i
        className={`ti ${icon}`}
        style={{ fontSize: 20, color: tone ? `var(--color-text-${tone})` : 'var(--color-text-info)' }}
        aria-hidden
      />
      <div>
        <div style={{ fontSize: 22, fontWeight: 700, color: tone ? `var(--color-text-${tone})` : undefined }}>
          {value.toLocaleString()}
        </div>
        <div className="muted" style={{ fontSize: 12 }}>
          {label}
        </div>
      </div>
    </div>
  )
}
