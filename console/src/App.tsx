import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { AppShell } from './components/AppShell'
import { Spinner } from './components/ui'
import { Login } from './pages/Login'
import { Dashboard } from './pages/Dashboard'
import { Connect } from './pages/Connect'
import { Monitor } from './pages/Monitor'
import { AuditLog } from './pages/AuditLog'
import { RequestDetail } from './pages/RequestDetail'
import { Policies } from './pages/Policies'
import { PolicyEditor } from './pages/PolicyEditor'
import { ModelsRouting } from './pages/ModelsRouting'
import { UsageCost } from './pages/UsageCost'
import { Users } from './pages/Users'
import { TeamRoles } from './pages/TeamRoles'
import { ApiKeys } from './pages/ApiKeys'
import { Settings } from './pages/Settings'

export function App() {
  const { tenant, loading } = useAuth()

  if (loading) {
    return (
      <div
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--color-text-tertiary)',
        }}
      >
        <Spinner /> &nbsp;Loading Auvex…
      </div>
    )
  }

  if (!tenant) {
    return (
      <Routes>
        <Route path="*" element={<Login />} />
      </Routes>
    )
  }

  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/connect" element={<Connect />} />
        <Route path="/monitor" element={<Monitor />} />
        <Route path="/audit" element={<AuditLog />} />
        <Route path="/audit/:id" element={<RequestDetail />} />
        <Route path="/policies" element={<Policies />} />
        <Route path="/policies/new" element={<PolicyEditor />} />
        <Route path="/policies/:id" element={<PolicyEditor />} />
        <Route path="/models" element={<ModelsRouting />} />
        <Route path="/usage" element={<UsageCost />} />
        <Route path="/users" element={<Users />} />
        <Route path="/team" element={<TeamRoles />} />
        <Route path="/keys" element={<ApiKeys />} />
        <Route path="/settings" element={<Settings />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
