import { useState, type FormEvent } from 'react'
import { ApiError, api } from '../lib/api'
import { useApi } from '../lib/useApi'
import { useT } from '../lib/i18n'
import type { Member } from '../lib/types'
import type { Column } from '../components/ui'
import {
  Alert,
  Avatar,
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
  Select,
  TextField,
  ToastProvider,
  useToast,
} from '../components/ui'

const ROLES = ['owner', 'security_admin', 'auditor'] as const

// Maps a backend role onto its translation key (security_admin uses camelCase in i18n).
const ROLE_KEY: Record<string, string> = {
  owner: 'role.owner',
  security_admin: 'role.securityAdmin',
  auditor: 'role.auditor',
}

function roleTone(role: string): 'info' | 'warning' | 'success' {
  if (role === 'owner') return 'warning'
  if (role === 'security_admin') return 'info'
  return 'success'
}

function TeamRolesInner() {
  const { t } = useT()
  const tr = t as (k: string) => string
  const { toast } = useToast()
  const members = useApi(() => api.members(), [])

  const roleLabel = (role: string) => tr(ROLE_KEY[role] ?? role)
  const roleOptions = ROLES.map((r) => ({ value: r, label: roleLabel(r) }))

  const [showForm, setShowForm] = useState(false)
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [role, setRole] = useState<string>('auditor')
  const [formError, setFormError] = useState<string>()
  const [saving, setSaving] = useState(false)

  function openForm() {
    setEmail('')
    setName('')
    setRole('auditor')
    setFormError(undefined)
    setShowForm(true)
  }

  async function invite(e?: FormEvent) {
    e?.preventDefault()
    setFormError(undefined)
    setSaving(true)
    try {
      await api.createMember({ email: email.trim(), name: name.trim() || undefined, role })
      setEmail('')
      setName('')
      setRole('auditor')
      setShowForm(false)
      await members.reload()
      toast(tr('team.toastInvited'), 'success')
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : tr('team.errInvite'))
    } finally {
      setSaving(false)
    }
  }

  async function changeRole(m: Member, newRole: string) {
    await api.updateMember(m.id, {
      email: m.email,
      name: m.name ?? undefined,
      role: newRole,
      status: m.status,
    })
    await members.reload()
    toast(tr('team.toastRole'), 'success')
  }

  async function remove(m: Member) {
    await api.deleteMember(m.id)
    await members.reload()
    toast(tr('team.toastRemoved'), 'success')
  }

  const inviteBtn = (
    <Button variant="primary" icon="ti-user-plus" onClick={openForm}>
      {tr('team.invite')}
    </Button>
  )

  if (members.loading) return <Loading />
  if (members.error || !members.data)
    return <ErrorState message={members.error ?? tr('common.noData')} onRetry={members.reload} />

  const data = members.data

  const columns: Column<Member>[] = [
    {
      key: 'member',
      header: tr('team.colMember'),
      width: '1.6fr',
      render: (m) => (
        <span style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
          <Avatar name={m.name || m.email} size={30} />
          <span style={{ minWidth: 0, display: 'flex', flexDirection: 'column' }}>
            <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {m.name || m.email}
            </span>
            <span
              className="muted"
              style={{
                fontSize: 11,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {m.email}
            </span>
          </span>
        </span>
      ),
    },
    {
      key: 'role',
      header: tr('team.colRole'),
      width: '1fr',
      render: (m) => (
        <span style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0, width: '100%' }}>
          <Chip tone={roleTone(m.role)} size="sm">
            {roleLabel(m.role)}
          </Chip>
          <span style={{ flex: 1, minWidth: 90 }}>
            <Select
              value={m.role}
              onChange={(v) => void changeRole(m, v)}
              options={roleOptions}
              fullWidth
            />
          </span>
        </span>
      ),
    },
    {
      key: 'status',
      header: tr('team.colStatus'),
      width: '90px',
      render: (m) => (
        <Chip tone={m.status === 'active' ? 'success' : 'warning'} size="sm">
          {m.status}
        </Chip>
      ),
    },
    {
      key: 'actions',
      header: '',
      width: '50px',
      align: 'right',
      render: (m) => (
        <IconButton
          icon="ti-trash"
          label={tr('common.delete')}
          size="sm"
          onClick={() => void remove(m)}
        />
      ),
    },
  ]

  return (
    <Card>
      <CardHeader
        icon="ti-users-group"
        title={tr('nav.team')}
        subtitle={tr('team.subtitle')}
        actions={data.length > 0 ? inviteBtn : undefined}
      />

      {data.length === 0 ? (
        <EmptyState
          icon="ti-users-group"
          title={tr('team.emptyTitle')}
          message={tr('team.emptyMsg')}
          action={inviteBtn}
        />
      ) : (
        <DataTable columns={columns} rows={data} rowKey={(m) => m.id} />
      )}

      <Dialog
        open={showForm}
        onClose={() => setShowForm(false)}
        title={tr('team.inviteTitle')}
        actions={
          <>
            <Button variant="ghost" onClick={() => setShowForm(false)}>
              {tr('common.cancel')}
            </Button>
            <Button
              variant="primary"
              icon="ti-send"
              loading={saving}
              disabled={!email.trim()}
              onClick={() => void invite()}
            >
              {tr('common.send')}
            </Button>
          </>
        }
      >
        <form
          onSubmit={(e) => void invite(e)}
          style={{ display: 'flex', flexDirection: 'column', gap: 14 }}
        >
          {formError && <Alert tone="danger">{formError}</Alert>}
          <TextField
            label={tr('team.email')}
            type="email"
            value={email}
            onChange={setEmail}
            placeholder="teammate@company.com"
            icon="ti-mail"
            fullWidth
          />
          <TextField
            label={tr('team.name')}
            value={name}
            onChange={setName}
            placeholder={tr('common.optional')}
            icon="ti-user"
            fullWidth
          />
          <Select label={tr('team.role')} value={role} onChange={setRole} options={roleOptions} fullWidth />
          {/* Hidden submit so pressing Enter inside a field submits the form. */}
          <button type="submit" style={{ display: 'none' }} aria-hidden tabIndex={-1} />
        </form>
      </Dialog>
    </Card>
  )
}

export function TeamRoles() {
  return (
    <ToastProvider>
      <TeamRolesInner />
    </ToastProvider>
  )
}
