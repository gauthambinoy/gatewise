import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '../lib/api'
import { useApi } from '../lib/useApi'
import { useT } from '../lib/i18n'
import { ErrorState, Loading } from '../components/ui'
import { Card, CardHeader, Alert } from '../components/ui'
import { Button, TextField, Select, Switch } from '../components/ui'
import type { Policy, PolicyInput } from '../lib/types'

type Effect = Policy['effect']
type ResourceType = Policy['resourceType']

const EFFECT_OPTIONS: { value: Effect; label: string }[] = [
  { value: 'allow', label: 'Allow' },
  { value: 'deny', label: 'Deny' },
  { value: 'redact', label: 'Redact' },
]

const RESOURCE_TYPE_OPTIONS: { value: ResourceType; label: string }[] = [
  { value: 'model', label: 'model' },
  { value: 'data_type', label: 'data_type' },
  { value: 'user', label: 'user' },
]

export function PolicyEditor() {
  const { id } = useParams()
  const editing = Boolean(id)
  const navigate = useNavigate()
  const { t } = useT()
  const tr = t as (k: string) => string

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
    <Card>
      <CardHeader
        icon="ti-shield-plus"
        title={editing ? 'Edit policy' : 'New policy'}
        subtitle={
          editing
            ? 'Update this rule — changes apply to new requests immediately.'
            : 'Add a rule to govern which models and data types are allowed.'
        }
      />

      <form onSubmit={save} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <TextField
          label="Policy name"
          value={name}
          onChange={setName}
          placeholder="redact-PII-external"
          icon="ti-shield"
          fullWidth
        />

        <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end' }}>
          <div style={{ flex: 1 }}>
            <Select
              label="When data type is"
              value={resourceType}
              onChange={(v) => setResourceType(v as ResourceType)}
              options={RESOURCE_TYPE_OPTIONS}
              fullWidth
            />
          </div>
          <div style={{ flex: 1 }}>
            <TextField
              label="And model is"
              value={resourceValue}
              onChange={setResourceValue}
              placeholder="e.g. email, gpt-4, alice@corp.com"
              fullWidth
            />
          </div>
          <div style={{ width: 110 }}>
            <TextField
              label="Priority"
              type="number"
              value={String(priority)}
              onChange={(v) => setPriority(Number(v))}
              fullWidth
            />
          </div>
        </div>

        <div style={{ maxWidth: 220 }}>
          <Select
            label="Then take action"
            value={effect}
            onChange={(v) => setEffect(v as Effect)}
            options={EFFECT_OPTIONS}
            fullWidth
          />
        </div>

        <Switch checked={enabled} onChange={setEnabled} label="Enabled" />

        <Alert tone="info" icon="ti-info-circle">
          If no rule matches, Auvex defaults to the safest option (deny unknown).
        </Alert>

        {error && <Alert tone="danger">{error}</Alert>}

        <div style={{ display: 'flex', gap: 10 }}>
          <Button type="submit" variant="primary" loading={submitting}>
            {editing ? tr('common.save') : tr('common.create')}
          </Button>
          <Button variant="ghost" onClick={() => navigate('/policies')}>
            {tr('common.cancel')}
          </Button>
        </div>
      </form>
    </Card>
  )
}
