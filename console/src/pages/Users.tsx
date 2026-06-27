import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { useT } from '../lib/i18n'
import type { UserUsage } from '../lib/types'
import type { Column } from '../components/ui'
import {
  Avatar,
  Card,
  CardHeader,
  Chip,
  DataTable,
  EmptyState,
  ErrorState,
  Loading,
  Stat,
  money,
  num,
} from '../components/ui'

export function Users() {
  const { t } = useT()
  const tr = t as (k: string) => string
  const users = useApi(() => api.usageByUser(), [])

  if (users.loading) return <Loading />
  if (users.error || !users.data)
    return <ErrorState message={users.error ?? tr('common.noData')} onRetry={users.reload} />

  const data = users.data
  const totalRequests = data.reduce((s, u) => s + u.requests, 0)
  const flagged = data.filter((u) => u.blocked > 0).length
  const totalCost = data.reduce((s, u) => s + u.costUsd, 0)

  const columns: Column<UserUsage>[] = [
    {
      key: 'person',
      header: tr('users.colPerson'),
      render: (u) => {
        const flaggedRow = u.blocked > 0
        return (
          <span style={{ display: 'flex', alignItems: 'center', gap: 9, minWidth: 0 }}>
            <Avatar name={u.actor} size={28} />
            <span
              style={{
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                color: flaggedRow ? 'var(--color-text-danger)' : undefined,
              }}
            >
              {u.actor}
            </span>
            {flaggedRow && (
              <i
                className="ti ti-flag"
                aria-hidden
                style={{ fontSize: 13, color: 'var(--color-text-danger)', flexShrink: 0 }}
              />
            )}
          </span>
        )
      },
    },
    {
      key: 'requests',
      header: tr('users.colRequests'),
      width: '90px',
      align: 'right',
      render: (u) => num(u.requests),
    },
    {
      key: 'redacted',
      header: tr('users.colRedacted'),
      width: '90px',
      align: 'right',
      render: (u) => <span style={{ color: 'var(--color-text-info)' }}>{num(u.redacted)}</span>,
    },
    {
      key: 'blocked',
      header: tr('users.colBlocked'),
      width: '90px',
      align: 'right',
      render: (u) =>
        u.blocked > 0 ? (
          <Chip tone="danger" size="sm">
            {num(u.blocked)}
          </Chip>
        ) : (
          <span style={{ color: 'var(--color-text-tertiary)' }}>0</span>
        ),
    },
    {
      key: 'cost',
      header: tr('users.colCost'),
      width: '90px',
      align: 'right',
      render: (u) => <span className="sub">{money(u.costUsd)}</span>,
    },
  ]

  return (
    <Card>
      <CardHeader icon="ti-users" title={tr('nav.users')} subtitle={tr('users.subtitle')} />

      {data.length === 0 ? (
        <EmptyState
          icon="ti-users"
          title={tr('users.emptyTitle')}
          message={tr('users.emptyMsg')}
        />
      ) : (
        <>
          <div className="stat-grid" style={{ marginBottom: 18 }}>
            <Stat label={tr('users.total')} value={num(data.length)} />
            <Stat label={tr('dash.totalRequests')} value={num(totalRequests)} />
            <Stat label={tr('users.flagged')} value={num(flagged)} tone="danger" />
            <Stat label={tr('dash.totalCost')} value={money(totalCost)} />
          </div>

          <DataTable columns={columns} rows={data} rowKey={(u) => u.actor} />
        </>
      )}
    </Card>
  )
}
