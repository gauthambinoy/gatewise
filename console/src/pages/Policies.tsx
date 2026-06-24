import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { useT } from '../lib/i18n'
import { Badge, EmptyState, ErrorState, Loading } from '../components/ui'
import { Card, CardHeader, Chip } from '../components/ui'
import { Button, IconButton } from '../components/ui'
import { DataTable, type Column } from '../components/ui'
import type { Policy } from '../lib/types'

function effectTone(effect: Policy['effect']) {
  if (effect === 'allow') return 'success' as const
  if (effect === 'deny') return 'danger' as const
  return 'info' as const
}

const EFFECT_ICON: Record<Policy['effect'], string> = {
  allow: 'ti-check',
  deny: 'ti-ban',
  redact: 'ti-eye-off',
}

export function Policies() {
  const { t } = useT()
  const tr = t as (k: string) => string
  const navigate = useNavigate()
  const policies = useApi(() => api.policies(), [])
  const [deleting, setDeleting] = useState<string>()

  async function remove(id: string) {
    setDeleting(id)
    try {
      await api.deletePolicy(id)
      await policies.reload()
    } finally {
      setDeleting(undefined)
    }
  }

  const newButton = (
    <Button variant="primary" icon="ti-plus" onClick={() => navigate('/policies/new')}>
      New policy
    </Button>
  )

  const columns: Column<Policy>[] = [
    {
      key: 'name',
      header: 'Policy',
      width: '1.4fr',
      render: (p) => <span style={{ fontWeight: 500 }}>{p.name}</span>,
    },
    {
      key: 'resource',
      header: 'Applies to',
      width: '1.2fr',
      render: (p) => (
        <span className="sub" style={{ fontSize: 12 }}>
          {`${p.resourceType} : ${p.resourceValue}`}
        </span>
      ),
    },
    {
      key: 'priority',
      header: 'Priority',
      width: '90px',
      align: 'right',
      render: (p) => <span className="muted" style={{ fontSize: 12 }}>{p.priority}</span>,
    },
    {
      key: 'effect',
      header: 'Action',
      width: '110px',
      render: (p) => (
        <Chip tone={effectTone(p.effect)} icon={EFFECT_ICON[p.effect]} size="sm">
          {p.effect}
        </Chip>
      ),
    },
    {
      key: 'enabled',
      header: 'Status',
      width: '90px',
      render: (p) =>
        p.enabled ? (
          <Badge tone="success">active</Badge>
        ) : (
          <Badge>disabled</Badge>
        ),
    },
    {
      key: 'actions',
      header: '',
      width: '80px',
      align: 'right',
      render: (p) => (
        <span style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
          <IconButton
            icon="ti-edit"
            label={tr('common.edit')}
            size="sm"
            onClick={() => navigate(`/policies/${p.id}`)}
          />
          <IconButton
            icon="ti-trash"
            label={tr('common.delete')}
            size="sm"
            disabled={deleting === p.id}
            onClick={() => remove(p.id)}
          />
        </span>
      ),
    },
  ]

  return (
    <Card>
      <CardHeader
        icon="ti-shield-check"
        title="Policies"
        subtitle="Rules run by priority — most-restrictive match wins (deny > redact > allow)."
        actions={newButton}
      />

      {policies.loading ? (
        <Loading />
      ) : policies.error || !policies.data ? (
        <ErrorState message={policies.error ?? 'No data'} onRetry={policies.reload} />
      ) : policies.data.length === 0 ? (
        <EmptyState
          icon="ti-shield-check"
          title="No policies yet"
          message="Add a rule to start governing which models and data types are allowed."
          action={newButton}
        />
      ) : (
        <DataTable
          columns={columns}
          rows={policies.data}
          rowKey={(p) => p.id}
        />
      )}
    </Card>
  )
}
