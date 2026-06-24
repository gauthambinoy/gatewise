import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
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
} from '../components/ui'

export function Users() {
  const users = useApi(() => api.usageByUser(), [])

  if (users.loading) return <Loading />
  if (users.error || !users.data)
    return <ErrorState message={users.error ?? 'No data'} onRetry={users.reload} />

  const data = users.data
  const totalRequests = data.reduce((s, u) => s + u.requests, 0)
  const flagged = data.filter((u) => u.blocked > 0).length
  const totalCost = data.reduce((s, u) => s + u.costUsd, 0)

  const columns: Column<UserUsage>[] = [
    {
      key: 'person',
      header: 'Person',
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
      header: 'Requests',
      width: '90px',
      align: 'right',
      render: (u) => u.requests.toLocaleString(),
    },
    {
      key: 'redacted',
      header: 'Redacted',
      width: '90px',
      align: 'right',
      render: (u) => (
        <span style={{ color: 'var(--color-text-info)' }}>{u.redacted.toLocaleString()}</span>
      ),
    },
    {
      key: 'blocked',
      header: 'Blocked',
      width: '90px',
      align: 'right',
      render: (u) =>
        u.blocked > 0 ? (
          <Chip tone="danger" size="sm">
            {u.blocked.toLocaleString()}
          </Chip>
        ) : (
          <span style={{ color: 'var(--color-text-tertiary)' }}>0</span>
        ),
    },
    {
      key: 'cost',
      header: 'Cost',
      width: '90px',
      align: 'right',
      render: (u) => <span className="sub">{money(u.costUsd)}</span>,
    },
  ]

  return (
    <Card>
      <CardHeader
        icon="ti-users"
        title="Users"
        subtitle="Everyone calling the gateway, by the user attached to each request. Spot risky usage at a glance."
      />

      {data.length === 0 ? (
        <EmptyState
          icon="ti-users"
          title="No user activity yet"
          message="Per-user metrics appear once requests include a user. Send traffic through the gateway to populate this."
        />
      ) : (
        <>
          <div className="stat-grid" style={{ marginBottom: 18 }}>
            <Stat label="Total users" value={data.length.toLocaleString()} />
            <Stat label="Total requests" value={totalRequests.toLocaleString()} />
            <Stat label="Flagged" value={flagged.toLocaleString()} tone="danger" />
            <Stat label="Total cost" value={money(totalCost)} />
          </div>

          <DataTable columns={columns} rows={data} rowKey={(u) => u.actor} />
        </>
      )}
    </Card>
  )
}
