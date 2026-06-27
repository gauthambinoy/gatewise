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

const EFFECTS: Effect[] = ['allow', 'deny', 'redact']
const RESOURCE_TYPES: ResourceType[] = ['model', 'data_type', 'user']
const RES_TYPE_KEY: Record<ResourceType, string> = {
  model: 'res.model',
  data_type: 'res.dataType',
  user: 'res.user',
}

export function PolicyEditor() {
  const { id } = useParams()
  const editing = Boolean(id)
  const navigate = useNavigate()
  const { t } = useT()
  const tr = t as (k: string) => string

  const effectOptions = EFFECTS.map((value) => ({ value, label: tr(`effect.${value}`) }))
  const resourceTypeOptions = RESOURCE_TYPES.map((value) => ({
    value,
    label: tr(RES_TYPE_KEY[value]),
  }))

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
      setError(err instanceof ApiError ? err.message : tr('common.somethingWrong'))
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
        title={editing ? tr('pol.editTitle') : tr('pol.new')}
        subtitle={editing ? tr('pol.editSubtitle') : tr('pol.newSubtitle')}
      />

      <form onSubmit={save} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <TextField
          label={tr('pol.fieldName')}
          value={name}
          onChange={setName}
          placeholder="redact-PII-external"
          icon="ti-shield"
          fullWidth
        />

        <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end' }}>
          <div style={{ flex: 1 }}>
            <Select
              label={tr('pol.whenType')}
              value={resourceType}
              onChange={(v) => setResourceType(v as ResourceType)}
              options={resourceTypeOptions}
              fullWidth
            />
          </div>
          <div style={{ flex: 1 }}>
            <TextField
              label={tr('pol.andValue')}
              value={resourceValue}
              onChange={setResourceValue}
              placeholder="e.g. email, gpt-4, alice@corp.com"
              fullWidth
            />
          </div>
          <div style={{ width: 110 }}>
            <TextField
              label={tr('pol.colPriority')}
              type="number"
              value={String(priority)}
              onChange={(v) => setPriority(Number(v))}
              fullWidth
            />
          </div>
        </div>

        <div style={{ maxWidth: 220 }}>
          <Select
            label={tr('pol.thenAction')}
            value={effect}
            onChange={(v) => setEffect(v as Effect)}
            options={effectOptions}
            fullWidth
          />
        </div>

        <Switch checked={enabled} onChange={setEnabled} label={tr('pol.enabled')} />

        <Alert tone="info" icon="ti-info-circle">
          {tr('pol.defaultDeny')}
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
