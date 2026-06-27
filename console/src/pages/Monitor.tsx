import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import { useT } from '../lib/i18n'
import type { AuditEntry, UsageSummary } from '../lib/types'
import {
  Badge,
  Button,
  Card,
  CardHeader,
  EmptyState,
  ErrorState,
  Loading,
  Select,
  clock,
  num,
  verdictTone,
} from '../components/ui'

const POLL_MS = 3000

export function Monitor() {
  const { t } = useT()
  const tr = t as (k: string, vars?: Record<string, string | number>) => string
  const [entries, setEntries] = useState<AuditEntry[]>([])
  const [usage, setUsage] = useState<UsageSummary | null>(null)
  const [live, setLive] = useState(true)
  const [verdict, setVerdict] = useState('')
  const [freshIds, setFreshIds] = useState<Set<number>>(new Set())
  // 'loading' until the first poll resolves; after that, transient poll errors keep the last good view.
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [reloadKey, setReloadKey] = useState(0)
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
        setStatus('ready')
      } catch {
        // Only surface an error on the very first load; later failures are transient, so we keep
        // the last good view rather than flashing an error on every dropped poll.
        if (active) setStatus((s) => (s === 'loading' ? 'error' : s))
      }
    }
    void poll()
    const id = live ? window.setInterval(poll, POLL_MS) : undefined
    return () => {
      active = false
      if (id) window.clearInterval(id)
    }
  }, [live, verdict, reloadKey])

  const verdictOptions = [
    { value: '', label: tr('common.allVerdicts') },
    { value: 'allowed', label: tr('verdict.allowed') },
    { value: 'redacted', label: tr('verdict.redacted') },
    { value: 'blocked', label: tr('verdict.blocked') },
  ]

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
            <span style={{ fontSize: 22, fontWeight: 700, letterSpacing: '-0.02em' }}>
              {tr('nav.monitor')}
            </span>
            <span
              className="badge"
              role="status"
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
                aria-hidden
                style={{
                  width: 7,
                  height: 7,
                  borderRadius: '50%',
                  background: live ? 'var(--color-text-success)' : 'var(--color-text-tertiary)',
                  boxShadow: live ? '0 0 0 0 rgba(52,211,153,0.6)' : 'none',
                  animation: live ? 'dotPulseLive 1.6s ease-in-out infinite' : 'none',
                }}
              />
              {live ? tr('monitor.live') : tr('monitor.paused')}
            </span>
          </div>
          <div className="muted" style={{ fontSize: 13, marginTop: 2 }}>
            {tr('monitor.subtitle')}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <Select value={verdict} onChange={setVerdict} options={verdictOptions} />
          <Button
            variant={live ? 'secondary' : 'primary'}
            icon={live ? 'ti-player-pause' : 'ti-player-play'}
            onClick={() => setLive((v) => !v)}
          >
            {live ? tr('monitor.pause') : tr('monitor.resume')}
          </Button>
        </div>
      </div>

      {usage && (
        <div className="stat-grid" style={{ marginBottom: 16 }}>
          <MiniStat label={tr('monitor.totalCalls')} value={usage.totalCalls} icon="ti-activity" />
          <MiniStat label={tr('verdict.allowed')} value={usage.allowed} icon="ti-circle-check" tone="success" />
          <MiniStat label={tr('verdict.redacted')} value={usage.redacted} icon="ti-eye-off" tone="info" />
          <MiniStat label={tr('verdict.blocked')} value={usage.blocked} icon="ti-ban" tone="danger" />
        </div>
      )}

      <Card padding="8px 0">
        <CardHeader
          title={tr('monitor.feed')}
          subtitle={tr('monitor.refreshing', { sec: POLL_MS / 1000 })}
          icon="ti-radar-2"
          actions={
            <span className="muted" style={{ fontSize: 12 }}>
              {tr('monitor.shown', { count: entries.length })}
            </span>
          }
        />
        <div>
          {status === 'loading' ? (
            <div style={{ padding: '0 18px 12px' }}>
              <Loading />
            </div>
          ) : status === 'error' ? (
            <div style={{ padding: '0 18px 12px' }}>
              <ErrorState
                message={tr('connect.errTitle')}
                onRetry={() => {
                  setStatus('loading')
                  setReloadKey((k) => k + 1)
                }}
              />
            </div>
          ) : entries.length === 0 ? (
            <div style={{ padding: '8px 18px 18px' }}>
              <EmptyState
                icon="ti-radar-2"
                title={tr('monitor.waitingTitle')}
                message={tr('monitor.waitingMsg')}
                action={
                  <Link to="/connect">
                    <Button variant="secondary" icon="ti-plug-connected">
                      {tr('monitor.goConnect')}
                    </Button>
                  </Link>
                }
              />
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
          {num(value)}
        </div>
        <div className="muted" style={{ fontSize: 12 }}>
          {label}
        </div>
      </div>
    </div>
  )
}
