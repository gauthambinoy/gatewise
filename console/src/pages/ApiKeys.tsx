import { useState, type FormEvent } from 'react'
import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { Badge, EmptyState, ErrorState, Loading, dt } from '../components/ui'
import type { Tone } from '../components/ui'

const GRID = '1.1fr 1.2fr 90px 120px 120px 70px'

function statusTone(status: string): Tone {
  return status === 'revoked' ? 'danger' : 'success'
}

export function ApiKeys() {
  const keys = useApi(() => api.keys(), [])
  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState('')
  const [creating, setCreating] = useState(false)
  const [secret, setSecret] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  async function create(e: FormEvent) {
    e.preventDefault()
    const trimmed = name.trim()
    if (!trimmed || creating) return
    setCreating(true)
    try {
      const created = await api.createKey(trimmed)
      setSecret(created.secret)
      setCopied(false)
      setName('')
      setShowForm(false)
      keys.reload()
    } finally {
      setCreating(false)
    }
  }

  async function copy() {
    if (!secret) return
    await navigator.clipboard.writeText(secret)
    setCopied(true)
  }

  async function revoke(id: string) {
    await api.revokeKey(id)
    keys.reload()
  }

  const newKeyBtn = (
    <button
      className="btn-primary"
      style={{ padding: '8px 14px', fontSize: 13, display: 'flex', alignItems: 'center', gap: 6 }}
      onClick={() => setShowForm((s) => !s)}
    >
      <i className="ti ti-plus" style={{ fontSize: 16 }} />
      New key
    </button>
  )

  return (
    <div className="card">
      {secret && (
        <div
          className="hint"
          style={{
            background: 'var(--color-background-success)',
            color: 'var(--color-text-success)',
            alignItems: 'flex-start',
            flexDirection: 'column',
            gap: 8,
            marginBottom: 16,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, width: '100%' }}>
            <i className="ti ti-circle-check" style={{ fontSize: 16 }} />
            <span style={{ fontWeight: 500, flex: 1 }}>Key created</span>
            <button
              onClick={() => {
                setSecret(null)
                setCopied(false)
              }}
              style={{ padding: '4px 8px', fontSize: 12 }}
              aria-label="Dismiss"
            >
              <i className="ti ti-x" />
            </button>
          </div>
          <div
            className="mono"
            style={{
              width: '100%',
              userSelect: 'all',
              wordBreak: 'break-all',
              fontSize: 13,
              color: 'var(--color-text-primary)',
              background: 'var(--color-background-primary)',
              border: '0.5px solid var(--color-border-tertiary)',
              borderRadius: 'var(--border-radius-md)',
              padding: '8px 10px',
            }}
          >
            {secret}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, width: '100%' }}>
            <button onClick={copy} style={{ padding: '6px 12px', fontSize: 12 }}>
              <i className={`ti ${copied ? 'ti-check' : 'ti-copy'}`} /> {copied ? 'Copied' : 'Copy'}
            </button>
            <span style={{ fontSize: 12 }}>Copy this now — it won't be shown again.</span>
          </div>
        </div>
      )}

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 2,
        }}
      >
        <div style={{ fontSize: 18, fontWeight: 500 }}>API keys</div>
        {newKeyBtn}
      </div>
      <div className="muted" style={{ fontSize: 12, marginBottom: showForm ? 12 : 18 }}>
        One key per app or team. Revoke instantly if leaked.
      </div>

      {showForm && (
        <form
          onSubmit={create}
          style={{ display: 'flex', gap: 8, marginBottom: 18, alignItems: 'center' }}
        >
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Key name (e.g. finance-app)"
            autoFocus
            style={{ flex: 1, maxWidth: 320 }}
          />
          <button type="submit" className="btn-primary" disabled={!name.trim() || creating} style={{ fontSize: 13 }}>
            {creating ? 'Creating…' : 'Create'}
          </button>
        </form>
      )}

      {keys.loading ? (
        <Loading />
      ) : keys.error || !keys.data ? (
        <ErrorState message={keys.error ?? 'No data'} onRetry={keys.reload} />
      ) : keys.data.length === 0 ? (
        <EmptyState
          icon="ti-key"
          title="No API keys yet"
          message="Create a key to authenticate apps against the gateway."
          action={newKeyBtn}
        />
      ) : (
        <>
          <div className="thead" style={{ display: 'grid', gridTemplateColumns: GRID, gap: 10 }}>
            <span>Name</span>
            <span>Key</span>
            <span>Status</span>
            <span>Last used</span>
            <span>Created</span>
            <span />
          </div>
          {keys.data.map((k) => (
            <div
              key={k.id}
              className="row"
              style={{
                display: 'grid',
                gridTemplateColumns: GRID,
                gap: 10,
                padding: '12px 8px',
                opacity: k.status === 'revoked' ? 0.55 : 1,
              }}
            >
              <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {k.name}
              </span>
              <span className="mono sub" style={{ fontSize: 12 }}>
                {k.prefix}…
              </span>
              <span>
                <Badge tone={statusTone(k.status)}>{k.status}</Badge>
              </span>
              <span className="muted" style={{ fontSize: 12 }}>
                {k.lastUsedAt ? dt(k.lastUsedAt) : '—'}
              </span>
              <span className="sub" style={{ fontSize: 12 }}>
                {dt(k.createdAt)}
              </span>
              <span>
                {k.status === 'active' && (
                  <button
                    onClick={() => revoke(k.id)}
                    style={{
                      padding: '4px 8px',
                      fontSize: 12,
                      color: 'var(--color-text-danger)',
                    }}
                  >
                    Revoke
                  </button>
                )}
              </span>
            </div>
          ))}
        </>
      )}
    </div>
  )
}
