import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { Badge, EmptyState, ErrorState, Loading } from '../components/ui'
import type { Policy } from '../lib/types'

const GRID = '1.4fr 1.2fr 90px 80px 56px'

function effectTone(effect: Policy['effect']) {
  if (effect === 'allow') return 'success' as const
  if (effect === 'deny') return 'danger' as const
  return 'info' as const
}

export function Policies() {
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

  return (
    <div className="card">
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 2,
        }}
      >
        <div style={{ fontSize: 18, fontWeight: 500 }}>Policies</div>
        <Link
          to="/policies/new"
          className="btn-primary"
          style={{
            padding: '8px 14px',
            fontSize: 13,
            display: 'flex',
            alignItems: 'center',
            gap: 6,
          }}
        >
          <i className="ti ti-plus" /> New policy
        </Link>
      </div>
      <div className="muted" style={{ fontSize: 12, marginBottom: 18 }}>
        Rules run by priority — most-restrictive match wins (deny &gt; redact &gt; allow).
      </div>

      {policies.loading ? (
        <Loading />
      ) : policies.error || !policies.data ? (
        <ErrorState message={policies.error ?? 'No data'} onRetry={policies.reload} />
      ) : policies.data.length === 0 ? (
        <EmptyState
          icon="ti-shield-check"
          title="No policies yet"
          message="Add a rule to start governing which models and data types are allowed."
          action={
            <Link
              to="/policies/new"
              className="btn-primary"
              style={{ padding: '8px 14px', fontSize: 13 }}
            >
              <i className="ti ti-plus" /> New policy
            </Link>
          }
        />
      ) : (
        <>
          <div
            className="thead"
            style={{ display: 'grid', gridTemplateColumns: GRID, gap: 10 }}
          >
            <span>Policy</span>
            <span>Applies to</span>
            <span>Action</span>
            <span>Status</span>
            <span />
          </div>
          {policies.data.map((p) => (
            <div
              key={p.id}
              className="row"
              style={{
                display: 'grid',
                gridTemplateColumns: GRID,
                gap: 10,
                alignItems: 'center',
                padding: '12px 8px',
              }}
            >
              <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {p.name}
              </span>
              <span className="sub" style={{ fontSize: 12 }}>
                {`${p.resourceType} : ${p.resourceValue}`}
              </span>
              <Badge tone={effectTone(p.effect)}>{p.effect}</Badge>
              {p.enabled ? (
                <span style={{ color: 'var(--color-text-success)', fontSize: 12 }}>● active</span>
              ) : (
                <span className="muted" style={{ fontSize: 12 }}>
                  ○ disabled
                </span>
              )}
              <span style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
                <Link
                  to={`/policies/${p.id}`}
                  className="muted"
                  aria-label="Edit policy"
                  style={{ padding: 4 }}
                >
                  <i className="ti ti-edit" style={{ fontSize: 16 }} />
                </Link>
                <button
                  onClick={() => remove(p.id)}
                  disabled={deleting === p.id}
                  aria-label="Delete policy"
                  style={{
                    padding: 4,
                    minHeight: 0,
                    border: 'none',
                    background: 'transparent',
                    color: 'var(--color-text-danger)',
                  }}
                >
                  <i className="ti ti-trash" style={{ fontSize: 16 }} />
                </button>
              </span>
            </div>
          ))}
        </>
      )}
    </div>
  )
}
