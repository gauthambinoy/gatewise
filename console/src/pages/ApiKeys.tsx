import { useState, type FormEvent } from 'react'
import { api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { useT } from '../lib/i18n'
import type { ApiKey } from '../lib/types'
import {
  Alert,
  Button,
  Card,
  CardHeader,
  Chip,
  DataTable,
  Dialog,
  EmptyState,
  ErrorState,
  IconButton,
  Loading,
  TextField,
  ToastProvider,
  dt,
  useToast,
} from '../components/ui'
import type { Column, Tone } from '../components/ui'

function statusTone(status: string): Tone {
  return status === 'revoked' ? 'danger' : 'success'
}

function ApiKeysInner() {
  const { t } = useT()
  const tr = t as (k: string) => string
  const { toast } = useToast()
  const keys = useApi(() => api.keys(), [])

  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState('')
  const [creating, setCreating] = useState(false)
  const [secret, setSecret] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  function openForm() {
    setName('')
    setShowForm(true)
  }

  async function create(e?: FormEvent) {
    e?.preventDefault()
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
      toast('Key created', 'success')
    } finally {
      setCreating(false)
    }
  }

  async function copy() {
    if (!secret) return
    await navigator.clipboard.writeText(secret)
    setCopied(true)
    toast('Copied to clipboard', 'success')
  }

  async function revoke(id: string) {
    await api.revokeKey(id)
    keys.reload()
    toast('Key revoked', 'info')
  }

  const newKeyBtn = (
    <Button variant="primary" icon="ti-plus" onClick={openForm}>
      New key
    </Button>
  )

  const columns: Column<ApiKey>[] = [
    {
      key: 'name',
      header: 'Name',
      width: '1.1fr',
      render: (k) => (
        <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {k.name}
        </span>
      ),
    },
    {
      key: 'prefix',
      header: 'Key',
      width: '1.2fr',
      render: (k) => (
        <span className="mono sub" style={{ fontSize: 12 }}>
          {k.prefix}…
        </span>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      width: '90px',
      render: (k) => (
        <Chip tone={statusTone(k.status)} size="sm">
          {k.status}
        </Chip>
      ),
    },
    {
      key: 'lastUsed',
      header: 'Last used',
      width: '120px',
      render: (k) => (
        <span className="muted" style={{ fontSize: 12 }}>
          {k.lastUsedAt ? dt(k.lastUsedAt) : '—'}
        </span>
      ),
    },
    {
      key: 'created',
      header: 'Created',
      width: '120px',
      render: (k) => (
        <span className="sub" style={{ fontSize: 12 }}>
          {dt(k.createdAt)}
        </span>
      ),
    },
    {
      key: 'actions',
      header: '',
      width: '70px',
      align: 'right',
      render: (k) =>
        k.status === 'active' ? (
          <Button variant="danger" size="sm" onClick={() => void revoke(k.id)}>
            Revoke
          </Button>
        ) : null,
    },
  ]

  return (
    <Card>
      <CardHeader
        icon="ti-key"
        title="API keys"
        subtitle="One key per app or team. Revoke instantly if leaked."
        actions={newKeyBtn}
      />

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
        <DataTable
          columns={columns}
          rows={keys.data}
          rowKey={(k) => k.id}
        />
      )}

      {/* Create-key form */}
      <Dialog
        open={showForm}
        onClose={() => setShowForm(false)}
        title="New API key"
        size="sm"
        actions={
          <>
            <Button variant="ghost" onClick={() => setShowForm(false)}>
              {tr('common.cancel')}
            </Button>
            <Button
              variant="primary"
              icon="ti-plus"
              loading={creating}
              disabled={!name.trim()}
              onClick={() => void create()}
            >
              {tr('common.create')}
            </Button>
          </>
        }
      >
        <form onSubmit={(e) => void create(e)}>
          <TextField
            label="Key name"
            value={name}
            onChange={setName}
            placeholder="Key name (e.g. finance-app)"
            icon="ti-tag"
            fullWidth
          />
          <button type="submit" style={{ display: 'none' }} aria-hidden tabIndex={-1} />
        </form>
      </Dialog>

      {/* One-time secret reveal */}
      <Dialog
        open={secret !== null}
        onClose={() => {
          setSecret(null)
          setCopied(false)
        }}
        title="Key created"
        size="md"
        actions={
          <Button
            variant="primary"
            onClick={() => {
              setSecret(null)
              setCopied(false)
            }}
          >
            Done
          </Button>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Alert tone="warning" title="Copy this now — it won't be shown again.">
            This is the only time the full secret is displayed. Store it somewhere safe.
          </Alert>
          <div style={{ display: 'flex', alignItems: 'stretch', gap: 8 }}>
            <div
              className="mono"
              style={{
                flex: 1,
                minWidth: 0,
                userSelect: 'all',
                wordBreak: 'break-all',
                fontSize: 13,
                color: 'var(--color-text-primary)',
                background: 'var(--color-background-secondary)',
                border: '0.5px solid var(--color-border-tertiary)',
                borderRadius: 'var(--border-radius-md)',
                padding: '8px 10px',
              }}
            >
              {secret}
            </div>
            <IconButton
              icon={copied ? 'ti-check' : 'ti-copy'}
              label={copied ? 'Copied' : 'Copy'}
              variant="solid"
              onClick={() => void copy()}
            />
          </div>
        </div>
      </Dialog>
    </Card>
  )
}

export function ApiKeys() {
  return (
    <ToastProvider>
      <ApiKeysInner />
    </ToastProvider>
  )
}
