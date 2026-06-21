import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { Badge, EmptyState, ErrorState, Loading } from '../components/ui'

const GRID = '1fr 16px 1.4fr 100px'

export function ModelsRouting() {
  const routes = useApi(() => api.models())

  return (
    <div className="card">
      <div style={{ fontSize: 18, fontWeight: 500, marginBottom: 2 }}>Models &amp; routing</div>
      <div className="muted" style={{ fontSize: 12, marginBottom: 18 }}>
        Give each model a friendly name. Switch providers by changing one line.
      </div>

      {routes.loading ? (
        <Loading />
      ) : routes.error || !routes.data ? (
        <ErrorState message={routes.error ?? 'No data'} onRetry={routes.reload} />
      ) : routes.data.length === 0 ? (
        <EmptyState
          icon="ti-route"
          title="No routes configured"
          message="Add a route to give a model a friendly name and point it at a provider."
        />
      ) : (
        <>
          <div className="thead" style={{ display: 'grid', gridTemplateColumns: GRID, gap: 10 }}>
            <span>Alias</span>
            <span />
            <span>Routes to</span>
            <span>Status</span>
          </div>
          {routes.data.map((r) => (
            <div
              key={r.alias}
              className="row"
              style={{
                display: 'grid',
                gridTemplateColumns: GRID,
                gap: 10,
                alignItems: 'center',
                padding: '12px 8px',
              }}
            >
              <span className="mono">{r.alias}</span>
              <i
                className="ti ti-arrow-right muted"
                style={{ fontSize: 15 }}
                aria-hidden
              />
              <span className="sub">{r.target}</span>
              <span>
                <Badge tone="success">configured</Badge>
              </span>
            </div>
          ))}
          <div className="hint" style={{ marginTop: 18 }}>
            <i className="ti ti-refresh" style={{ fontSize: 16 }} aria-hidden />
            If a provider becomes unreachable, Auvex automatically fails over to a configured backup
            provider.
          </div>
        </>
      )}
    </div>
  )
}
