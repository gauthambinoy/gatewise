import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { EmptyState, ErrorState, Loading, Stat, money } from '../components/ui'

const GRID = '1.7fr 90px 90px 80px 70px'

/** Two initials from an actor string (email, username, or "First Last"). */
function initials(actor: string): string {
  const parts = actor.replace(/[@.]/g, ' ').trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[1][0]).toUpperCase()
}

export function Users() {
  const users = useApi(() => api.usageByUser(), [])

  if (users.loading) return <Loading />
  if (users.error || !users.data)
    return <ErrorState message={users.error ?? 'No data'} onRetry={users.reload} />

  const data = users.data
  const totalRequests = data.reduce((s, u) => s + u.requests, 0)
  const flagged = data.filter((u) => u.blocked > 0).length
  const totalCost = data.reduce((s, u) => s + u.costUsd, 0)

  return (
    <div className="card">
      <div style={{ fontSize: 18, fontWeight: 600, marginBottom: 2 }}>Users</div>
      <div className="muted" style={{ fontSize: 12, marginBottom: 16 }}>
        Everyone calling the gateway, by the user attached to each request. Spot risky usage at a
        glance.
      </div>

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

          <div className="thead" style={{ display: 'grid', gridTemplateColumns: GRID, gap: 8 }}>
            <span>Person</span>
            <span>Requests</span>
            <span>Redacted</span>
            <span>Blocked</span>
            <span>Cost</span>
          </div>
          {data.map((u) => {
            const flaggedRow = u.blocked > 0
            return (
              <div
                key={u.actor}
                className="row"
                style={{
                  display: 'grid',
                  gridTemplateColumns: GRID,
                  gap: 8,
                  padding: '10px 8px',
                  background: flaggedRow ? 'var(--color-background-danger)' : undefined,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 9, minWidth: 0 }}>
                  <div
                    className="avatar"
                    style={
                      flaggedRow
                        ? {
                            width: 28,
                            height: 28,
                            fontSize: 11,
                            background: 'var(--color-background-danger)',
                            color: 'var(--color-text-danger)',
                          }
                        : { width: 28, height: 28, fontSize: 11 }
                    }
                  >
                    {initials(u.actor)}
                  </div>
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {u.actor}
                    {flaggedRow && (
                      <i
                        className="ti ti-flag"
                        style={{ fontSize: 13, color: 'var(--color-text-danger)', marginLeft: 6 }}
                      />
                    )}
                  </span>
                </div>
                <span>{u.requests.toLocaleString()}</span>
                <span style={{ color: 'var(--color-text-info)' }}>
                  {u.redacted.toLocaleString()}
                </span>
                <span
                  style={{
                    color: u.blocked > 0 ? 'var(--color-text-danger)' : 'var(--color-text-tertiary)',
                  }}
                >
                  {u.blocked.toLocaleString()}
                </span>
                <span className="sub">{money(u.costUsd)}</span>
              </div>
            )
          })}
        </>
      )}
    </div>
  )
}
