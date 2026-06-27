import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { useT } from '../lib/i18n'
import {
  Badge,
  Card,
  CardHeader,
  DataTable,
  EmptyState,
  ErrorState,
  Loading,
  Pagination,
  SearchInput,
  Select,
  clock,
  verdictTone,
} from '../components/ui'
import type { Column } from '../components/ui'
import type { AuditEntry } from '../lib/types'

const SIZE = 15
const VERDICTS = ['', 'allowed', 'redacted', 'blocked']

export function AuditLog() {
  const { t } = useT()
  const tr = t as (k: string, vars?: Record<string, string | number>) => string
  const navigate = useNavigate()

  const [verdict, setVerdict] = useState('')
  const [draft, setDraft] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)

  const audit = useApi(
    () => api.audit({ verdict: verdict || undefined, q: q || undefined, page, size: SIZE }),
    [verdict, q, page],
  )
  const verify = useApi(() => api.verify(), [])

  function search() {
    setPage(0)
    setQ(draft.trim())
  }

  const total = audit.data?.total ?? 0
  const pages = Math.max(1, Math.ceil(total / SIZE))

  const columns: Column<AuditEntry>[] = [
    {
      key: 'time',
      header: tr('audit.time'),
      width: '88px',
      render: (e) => (
        <span className="muted" style={{ fontSize: 12 }}>
          {clock(e.createdAt)}
        </span>
      ),
    },
    {
      key: 'request',
      header: tr('audit.request'),
      render: (e) => (
        <span>
          <span className="sub">{e.actor}</span> ·{' '}
          {e.promptRedacted ? `"${e.promptRedacted.slice(0, 60)}"` : '—'}
        </span>
      ),
    },
    {
      key: 'model',
      header: tr('audit.model'),
      width: '120px',
      render: (e) => (
        <span className="sub" style={{ fontSize: 12 }}>
          {e.model}
        </span>
      ),
    },
    {
      key: 'verdict',
      header: tr('audit.verdict'),
      width: '92px',
      render: (e) => <Badge tone={verdictTone(e.verdict)}>{e.verdict}</Badge>,
    },
  ]

  const verifyBadge = verify.data && (
    <Badge tone={verify.data.intact ? 'success' : 'danger'}>
      <i className={`ti ${verify.data.intact ? 'ti-lock-check' : 'ti-lock-open'}`} aria-hidden />{' '}
      {verify.data.intact
        ? tr('audit.chainVerified')
        : tr('audit.brokenAt', { id: verify.data.firstBrokenId ?? 0 })}
    </Badge>
  )

  return (
    <Card>
      <CardHeader title={tr('audit.title')} subtitle={tr('audit.subtitle')} actions={verifyBadge} />

      <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap' }}>
        <div style={{ flex: 1, minWidth: 200 }}>
          <SearchInput
            value={draft}
            onChange={setDraft}
            onSubmit={search}
            placeholder={tr('audit.searchPlaceholder')}
          />
        </div>
        <Select
          value={verdict}
          onChange={(v) => {
            setPage(0)
            setVerdict(v)
          }}
          options={VERDICTS.map((v) => ({
            value: v,
            label: v ? tr(`verdict.${v}`) : tr('common.allVerdicts'),
          }))}
        />
      </div>

      {audit.loading ? (
        <Loading />
      ) : audit.error || !audit.data ? (
        <ErrorState message={audit.error ?? tr('common.noData')} onRetry={audit.reload} />
      ) : audit.data.entries.length === 0 ? (
        <EmptyState
          icon="ti-list-search"
          title={tr('audit.emptyTitle')}
          message={q || verdict ? tr('audit.emptyFiltered') : tr('audit.emptyDefault')}
        />
      ) : (
        <>
          <DataTable
            columns={columns}
            rows={audit.data.entries}
            rowKey={(e) => e.id}
            onRowClick={(e) => navigate(`/audit/${e.id}`)}
          />
          <Pagination
            page={page}
            pageCount={pages}
            total={total}
            onPage={setPage}
            itemLabel={total === 1 ? tr('audit.entry') : tr('audit.entries')}
          />
        </>
      )}
    </Card>
  )
}
