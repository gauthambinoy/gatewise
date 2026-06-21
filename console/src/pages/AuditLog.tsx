import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { Badge, EmptyState, ErrorState, Loading, clock, verdictTone } from '../components/ui'

const SIZE = 15
const VERDICTS = ['', 'allowed', 'redacted', 'blocked']
const GRID = '88px 1fr 120px 92px 28px'

export function AuditLog() {
  const [verdict, setVerdict] = useState('')
  const [draft, setDraft] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)

  const audit = useApi(
    () => api.audit({ verdict: verdict || undefined, q: q || undefined, page, size: SIZE }),
    [verdict, q, page],
  )
  const verify = useApi(() => api.verify(), [])

  function search(e: FormEvent) {
    e.preventDefault()
    setPage(0)
    setQ(draft.trim())
  }

  const total = audit.data?.total ?? 0
  const pages = Math.max(1, Math.ceil(total / SIZE))

  return (
    <div className="card">
      <div
        style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 2 }}
      >
        <div style={{ fontSize: 18, fontWeight: 500 }}>Audit log</div>
        {verify.data && (
          <Badge tone={verify.data.intact ? 'success' : 'danger'}>
            <i className={`ti ${verify.data.intact ? 'ti-lock-check' : 'ti-lock-open'}`} />{' '}
            {verify.data.intact ? 'Chain verified' : `Broken at #${verify.data.firstBrokenId}`}
          </Badge>
        )}
      </div>
      <div className="muted" style={{ fontSize: 12, marginBottom: 16 }}>
        Every request, kept forever, tamper-proof.
      </div>

      <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap' }}>
        <form onSubmit={search} style={{ flex: 1, minWidth: 200, display: 'flex', gap: 8 }}>
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="Search prompts, users, models…"
            style={{ flex: 1 }}
          />
          <button type="submit">
            <i className="ti ti-search" />
          </button>
        </form>
        <select
          value={verdict}
          onChange={(e) => {
            setPage(0)
            setVerdict(e.target.value)
          }}
        >
          {VERDICTS.map((v) => (
            <option key={v} value={v}>
              {v ? v[0].toUpperCase() + v.slice(1) : 'All verdicts'}
            </option>
          ))}
        </select>
      </div>

      {audit.loading ? (
        <Loading />
      ) : audit.error || !audit.data ? (
        <ErrorState message={audit.error ?? 'No data'} onRetry={audit.reload} />
      ) : audit.data.entries.length === 0 ? (
        <EmptyState
          icon="ti-list-search"
          title="No requests found"
          message={q || verdict ? 'No entries match your filters.' : 'Traffic will show up here once apps point at the gateway.'}
        />
      ) : (
        <>
          <div className="thead" style={{ display: 'grid', gridTemplateColumns: GRID, gap: 8 }}>
            <span>Time</span>
            <span>Request</span>
            <span>Model</span>
            <span>Verdict</span>
            <span />
          </div>
          {audit.data.entries.map((e) => (
            <Link
              key={e.id}
              to={`/audit/${e.id}`}
              className="row"
              style={{ display: 'grid', gridTemplateColumns: GRID, gap: 8, padding: '10px 8px' }}
            >
              <span className="muted" style={{ fontSize: 12 }}>
                {clock(e.createdAt)}
              </span>
              <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                <span className="sub">{e.actor}</span> ·{' '}
                {e.promptRedacted ? `"${e.promptRedacted.slice(0, 60)}"` : '—'}
              </span>
              <span className="sub" style={{ fontSize: 12 }}>
                {e.model}
              </span>
              <Badge tone={verdictTone(e.verdict)}>{e.verdict}</Badge>
              <i className="ti ti-chevron-right muted" />
            </Link>
          ))}
          <div className="tnote">
            <span>
              {total.toLocaleString()} {total === 1 ? 'entry' : 'entries'} · page {page + 1} of {pages}
            </span>
            <div style={{ display: 'flex', gap: 6 }}>
              <button disabled={page === 0} onClick={() => setPage((p) => p - 1)} style={{ padding: '5px 10px', fontSize: 12 }}>
                Prev
              </button>
              <button
                disabled={page + 1 >= pages}
                onClick={() => setPage((p) => p + 1)}
                style={{ padding: '5px 10px', fontSize: 12 }}
              >
                Next
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
