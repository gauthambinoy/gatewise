import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const NAV = [
  {
    section: 'Console',
    items: [
      { to: '/', icon: 'ti-layout-dashboard', label: 'Dashboard' },
      { to: '/audit', icon: 'ti-list-search', label: 'Audit log' },
      { to: '/users', icon: 'ti-users', label: 'Users' },
    ],
  },
  {
    section: 'Govern',
    items: [{ to: '/policies', icon: 'ti-shield-check', label: 'Policies' }],
  },
  {
    section: 'Operate',
    items: [
      { to: '/models', icon: 'ti-router', label: 'Models & routing' },
      { to: '/usage', icon: 'ti-chart-bar', label: 'Usage & cost' },
      { to: '/keys', icon: 'ti-key', label: 'API keys' },
      { to: '/team', icon: 'ti-users-group', label: 'Team & roles' },
      { to: '/settings', icon: 'ti-settings', label: 'Settings' },
    ],
  },
]

const TITLES: Record<string, string> = {
  '/': 'Dashboard',
  '/audit': 'Audit log',
  '/users': 'Users',
  '/policies': 'Policies',
  '/models': 'Models & routing',
  '/usage': 'Usage & cost',
  '/keys': 'API keys',
  '/team': 'Team & roles',
  '/settings': 'Settings',
}

function titleFor(pathname: string): string {
  if (TITLES[pathname]) return TITLES[pathname]
  if (pathname.startsWith('/audit/')) return 'Request detail'
  if (pathname.startsWith('/policies')) return 'Policy editor'
  return 'Auvex'
}

export function AppShell() {
  const { tenant, logout } = useAuth()
  const loc = useLocation()
  const [dark, setDark] = useState(document.body.classList.contains('dark'))

  function toggleTheme() {
    const next = !dark
    setDark(next)
    document.body.className = next ? 'dark' : 'light'
    localStorage.setItem('auvex.theme', next ? 'dark' : 'light')
  }

  const initials = (tenant?.name ?? 'A').slice(0, 2).toUpperCase()

  return (
    <div className="app">
      <nav className="side">
        <div className="brand">
          <div className="logo">
            <i className="ti ti-shield-lock" />
          </div>
          <span style={{ fontSize: 17, fontWeight: 600 }}>Auvex</span>
        </div>
        {NAV.map((group) => (
          <div key={group.section}>
            <div className="nav-section">{group.section}</div>
            {group.items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) => `navbtn${isActive ? ' active' : ''}`}
              >
                <i className={`ti ${item.icon}`} />
                <span>{item.label}</span>
              </NavLink>
            ))}
          </div>
        ))}
      </nav>
      <div className="main">
        <div className="top">
          <span style={{ fontSize: 15, fontWeight: 600 }}>{titleFor(loc.pathname)}</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <button className="themebtn" onClick={toggleTheme}>
              <i className={`ti ${dark ? 'ti-sun' : 'ti-moon'}`} />
              <span>{dark ? 'Light' : 'Dark'}</span>
            </button>
            <button className="themebtn" onClick={logout} title="Sign out" aria-label="Sign out">
              <i className="ti ti-logout-2" />
            </button>
            <div className="avatar" title={tenant?.name}>
              {initials}
            </div>
          </div>
        </div>
        <div className="content anim" key={loc.pathname}>
          <Outlet />
        </div>
      </div>
    </div>
  )
}
