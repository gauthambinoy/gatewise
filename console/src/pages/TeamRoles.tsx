import { useState, type FormEvent } from 'react'
import { ApiError, api } from '../lib/api'
import { useApi } from '../lib/useApi'
import type { Member } from '../lib/types'
import { Badge, EmptyState, ErrorState, Loading } from '../components/ui'

const GRID = '1.6fr 1fr 90px 50px'
const ROLES = ['owner', 'security_admin', 'auditor'] as const

function roleLabel(role: string): string {
  if (role === 'owner') return 'Owner'
  if (role === 'security_admin') return 'Security admin'
  if (role === 'auditor') return 'Auditor'
  return role
}

/** Two initials from a name or email. */
function initials(text: string): string {
  const parts = text.replace(/[@.]/g, ' ').trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[1][0]).toUpperCase()
}

export function TeamRoles() {
  const members = useApi(() => api.members(), [])
  const [showForm, setShowForm] = useState(false)
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [role, setRole] = useState<string>('auditor')
  const [formError, setFormError] = useState<string>()
  const [saving, setSaving] = useState(false)

  function openForm() {
    setFormError(undefined)
    setShowForm(true)
  }

  async function invite(e: FormEvent) {
    e.preventDefault()
    setFormError(undefined)
    setSaving(true)
    try {
      await api.createMember({ email: email.trim(), name: name.trim() || undefined, role })
      setEmail('')
      setName('')
      setRole('auditor')
      setShowForm(false)
      await members.reload()
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : 'Could not invite member')
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
  }

  async function remove(m: Member) {
    await api.deleteMember(m.id)
    await members.reload()
  }

  const inviteBtn = (
    <button className="btn-primary" onClick={openForm} style={{ padding: '8px 14px', fontSize: 13 }}>
      <i className="ti ti-user-plus" /> Invite
    </button>
  )

  if (members.loading) return <Loading />
  if (members.error || !members.data)
    return <ErrorState message={members.error ?? 'No data'} onRetry={members.reload} />

  const data = members.data

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
        <div style={{ fontSize: 18, fontWeight: 500 }}>Team &amp; roles</div>
        {inviteBtn}
      </div>
      <div className="muted" style={{ fontSize: 12, marginBottom: 18 }}>
        Control who can view logs, edit policy, or manage keys.
      </div>

      {showForm && (
        <form
          onSubmit={invite}
          style={{
            display: 'flex',
            gap: 8,
            flexWrap: 'wrap',
            alignItems: 'flex-end',
            marginBottom: 18,
            padding: 14,
            border: '0.5px solid var(--color-border-tertiary)',
            borderRadius: 'var(--border-radius-md)',
            background: 'var(--color-background-secondary)',
          }}
        >
          <div style={{ flex: 2, minWidth: 180 }}>
            <label>Email</label>
            <input
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="teammate@company.com"
              style={{ width: '100%' }}
            />
          </div>
          <div style={{ flex: 1, minWidth: 140 }}>
            <label>Name</label>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Optional"
              style={{ width: '100%' }}
            />
          </div>
          <div style={{ flex: 1, minWidth: 140 }}>
            <label>Role</label>
            <select value={role} onChange={(e) => setRole(e.target.value)} style={{ width: '100%' }}>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {roleLabel(r)}
                </option>
              ))}
            </select>
          </div>
          <button type="submit" className="btn-primary" disabled={saving} style={{ padding: '8px 16px', fontSize: 13 }}>
            <i className="ti ti-send" /> Send
          </button>
          {formError && (
            <div className="badge-danger" style={{ flexBasis: '100%', padding: '8px 12px', fontSize: 12 }}>
              {formError}
            </div>
          )}
        </form>
      )}

      {data.length === 0 ? (
        <EmptyState
          icon="ti-users-group"
          title="No team members yet"
          message="Invite teammates to give them console access."
          action={inviteBtn}
        />
      ) : (
        <>
          <div className="thead" style={{ display: 'grid', gridTemplateColumns: GRID, gap: 10 }}>
            <span>Member</span>
            <span>Role</span>
            <span>Status</span>
            <span />
          </div>
          {data.map((m) => (
            <div
              key={m.id}
              className="row"
              style={{ display: 'grid', gridTemplateColumns: GRID, gap: 10, padding: '11px 8px' }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
                <div className="avatar">{initials(m.name || m.email)}</div>
                <div style={{ minWidth: 0 }}>
                  <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {m.name || m.email}
                  </div>
                  <div className="muted" style={{ fontSize: 11, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {m.email}
                  </div>
                </div>
              </div>
              <select
                value={m.role}
                onChange={(e) => void changeRole(m, e.target.value)}
                style={{ width: '100%' }}
              >
                {ROLES.map((r) => (
                  <option key={r} value={r}>
                    {roleLabel(r)}
                  </option>
                ))}
              </select>
              <span>
                <Badge tone={m.status === 'active' ? 'success' : 'warning'}>{m.status}</Badge>
              </span>
              <button
                onClick={() => void remove(m)}
                title="Remove member"
                style={{ padding: '6px 8px', color: 'var(--color-text-danger)' }}
              >
                <i className="ti ti-trash" />
              </button>
            </div>
          ))}
        </>
      )}
    </div>
  )
}
