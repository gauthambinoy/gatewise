import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { useT } from '../lib/i18n'
import type { Route } from '../lib/types'
import {
  Card,
  CardHeader,
  Chip,
  DataTable,
  EmptyState,
  ErrorState,
  Loading,
  type Column,
} from '../components/ui'

/** Best-effort provider label from a routing target like "openai/gpt-4o" or "anthropic:claude". */
function providerOf(target: string): string {
  const head = target.split(/[/:]/)[0]?.trim()
  return head || target
}

export function ModelsRouting() {
  const { t } = useT()
  const tr = t as (k: string) => string
  const routes = useApi(() => api.models())

  const columns: Column<Route>[] = [
    {
      key: 'alias',
      header: 'Alias',
      width: '1fr',
      render: (r) => <span className="mono">{r.alias}</span>,
    },
    {
      key: 'target',
      header: 'Routes to',
      width: '1.6fr',
      render: (r) => (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
          <Chip tone="info" icon="ti-cpu" size="sm">
            {providerOf(r.target)}
          </Chip>
          <span className="sub" style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {r.target}
          </span>
        </span>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      width: '120px',
      render: () => (
        <Chip tone="success" icon="ti-circle-check" size="sm">
          configured
        </Chip>
      ),
    },
  ]

  return (
    <Card>
      <CardHeader
        icon="ti-route"
        title={tr('nav.models')}
        subtitle="Give each model a friendly name. Switch providers by changing one line."
      />

      {routes.loading ? (
        <Loading label={tr('common.loading')} />
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
          <DataTable columns={columns} rows={routes.data} rowKey={(r) => r.alias} />
          <div className="hint" style={{ marginTop: 18 }}>
            <i className="ti ti-refresh" style={{ fontSize: 16 }} aria-hidden />
            If a provider becomes unreachable, Auvex automatically fails over to a configured backup
            provider.
          </div>
        </>
      )}
    </Card>
  )
}
