import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '../lib/api'
import { useApi } from '../lib/useApi'
import { ErrorState, Loading } from '../components/ui'
import type { Policy, PolicyInput } from '../lib/types'

type Effect = Policy['effect']
type ResourceType = Policy['resourceType']

const RESOURCE_TYPES: ResourceType[] = ['model', 'data_type', 'user']

const ACTIONS: { effect: Effect; label: string; icon: string }[] = [
  { effect: 'redact', label: 'Redact', icon: 'ti-eye-off' },
  { effect: 'deny', label: 'Block', icon: 'ti-ban' },
  { effect: 'allow', label: 'Allow', icon: 'ti-check' },
]

export function PolicyEditor() {
  const { id } = useParams()
  const editing = Boolean(id)
  const navigate = useNavigate()

  const loaded = useApi(() => (id ? api.policy(id) : Promise.resolve(undefined)), [id])

  const [name, setName] = useState('')
  const [effect, setEffect] = useState<Effect>('redact')
  const [resourceType, setResourceType] = useState<ResourceType>('data_type')
  const [resourceValue, setResourceValue] = useState('')
  const [priority, setPriority] = useState(100)
  const [enabled, setEnabled] = useState(true)

  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string>()

  useEffect(() => {
    const p = loaded.data
    if (!p) return
    setName(p.name)
    setEffect(p.effect)
    setResourceType(p.resourceType)
    setResourceValue(p.resourceValue)
    setPriority(p.priority)
    setEnabled(p.enabled)
  }, [loaded.data])

  async function save(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(undefined)
    const body: PolicyInput = { name, effect, resourceType, resourceValue, priority, enabled }
    try {
      if (editing && id) await api.updatePolicy(id, body)
      else await api.createPolicy(body)
      navigate('/policies')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    } finally {
      setSubmitting(false)
    }
  }

  if (editing && loaded.loading) return <Loading />
  if (editing && loaded.error)
    return <ErrorState message={loaded.error} onRetry={loaded.reload} />

  return (
    <div className="card">
      <div style={{ fontSize: 18, fontWeight: 500, marginBottom: 2 }}>
        {editing ? 'Edit policy' : 'New policy'}
      </div>
      <div className="muted" style={{ fontSize: 12, marginBottom: 20 }}>
        {editing
          ? 'Update this rule — changes apply to new requests immediately.'
          : 'Add a rule to govern which models and data types are allowed.'}
      </div>

      <form onSubmit={save}>
        <label style={{ display: 'block', fontSize: 12, marginBottom: 4 }}>Policy name</label>
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="redact-PII-external"
          style={{ width: '100%', marginBottom: 16 }}
        />

        <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
          <div style={{ flex: 1 }}>
            <label style={{ display: 'block', fontSize: 12, marginBottom: 4 }}>When data type is</label>
            <select
              value={resourceType}
              onChange={(e) => setResourceType(e.target.value as ResourceType)}
              style={{ width: '100%' }}
            >
              {RESOURCE_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
          <div style={{ flex: 1 }}>
            <label style={{ display: 'block', fontSize: 12, marginBottom: 4 }}>And model is</label>
            <input
              type="text"
              value={resourceValue}
              onChange={(e) => setResourceValue(e.target.value)}
              placeholder="e.g. email, gpt-4, alice@corp.com"
              style={{ width: '100%' }}
            />
          </div>
          <div style={{ width: 110 }}>
            <label style={{ display: 'block', fontSize: 12, marginBottom: 4 }}>Priority</label>
            <input
              type="number"
              value={priority}
              onChange={(e) => setPriority(Number(e.target.value))}
              style={{ width: '100%' }}
            />
          </div>
        </div>

        <label style={{ display: 'block', fontSize: 12, marginBottom: 8 }}>Then take action</label>
        <div style={{ display: 'flex', gap: 10, marginBottom: 20 }}>
          {ACTIONS.map((a) => {
            const selected = effect === a.effect
            return (
              <button
                key={a.effect}
                type="button"
                onClick={() => setEffect(a.effect)}
                style={{
                  flex: 1,
                  border: selected
                    ? '2px solid var(--color-border-info)'
                    : '0.5px solid var(--color-border-secondary)',
                  borderRadius: 'var(--border-radius-md)',
                  padding: 12,
                  textAlign: 'center',
                  background: 'transparent',
                  minHeight: 0,
                }}
              >
                <i
                  className={`ti ${a.icon}`}
                  style={{
                    fontSize: 20,
                    color: selected ? 'var(--color-text-info)' : 'var(--color-text-secondary)',
                  }}
                />
                <div
                  style={{
                    fontSize: 13,
                    fontWeight: selected ? 500 : 400,
                    marginTop: 4,
                    color: selected ? 'var(--color-text-info)' : 'var(--color-text-secondary)',
                  }}
                >
                  {a.label}
                </div>
              </button>
            )
          })}
        </div>

        <label
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            fontSize: 13,
            marginBottom: 20,
            cursor: 'pointer',
          }}
        >
          <input
            type="checkbox"
            checked={enabled}
            onChange={(e) => setEnabled(e.target.checked)}
            style={{ width: 'auto', minHeight: 0 }}
          />
          Enabled
        </label>

        <div className="hint" style={{ marginBottom: 20 }}>
          <i className="ti ti-info-circle" style={{ fontSize: 16 }} />
          If no rule matches, Auvex defaults to the safest option (deny unknown).
        </div>

        {error && (
          <div
            className="badge badge-danger"
            style={{ display: 'block', textAlign: 'left', marginBottom: 16, padding: '8px 10px' }}
          >
            {error}
          </div>
        )}

        <div style={{ display: 'flex', gap: 10 }}>
          <button
            type="submit"
            className="btn-primary"
            disabled={submitting}
            style={{ padding: '11px 22px', fontSize: 13, fontWeight: 500 }}
          >
            {submitting ? 'Saving…' : 'Save policy'}
          </button>
          <button
            type="button"
            onClick={() => navigate('/policies')}
            style={{ padding: '11px 22px', fontSize: 13 }}
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  )
}
