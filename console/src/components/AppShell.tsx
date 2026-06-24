import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useT } from '../lib/i18n'
import { LanguageSwitcher } from './LanguageSwitcher'

const NAV = [
  {
    section: 'nav.console',
    items: [
      { to: '/', icon: 'ti-layout-dashboard', label: 'nav.dashboard' },
      { to: '/audit', icon: 'ti-list-search', label: 'nav.audit' },
      { to: '/users', icon: 'ti-users', label: 'nav.users' },
    ],
  },
  {
    section: 'nav.govern',
    items: [{ to: '/policies', icon: 'ti-shield-check', label: 'nav.policies' }],
  },
  {
    section: 'nav.operate',
    items: [
      { to: '/models', icon: 'ti-router', label: 'nav.models' },
      { to: '/usage', icon: 'ti-chart-bar', label: 'nav.usage' },
      { to: '/keys', icon: 'ti-key', label: 'nav.keys' },
      { to: '/team', icon: 'ti-users-group', label: 'nav.team' },
      { to: '/settings', icon: 'ti-settings', label: 'nav.settings' },
    ],
  },
]

const TITLE_KEYS: Record<string, string> = {
  '/': 'nav.dashboard',
  '/audit': 'nav.audit',
  '/users': 'nav.users',
  '/policies': 'nav.policies',
  '/models': 'nav.models',
  '/usage': 'nav.usage',
  '/keys': 'nav.keys',
  '/team': 'nav.team',
  '/settings': 'nav.settings',
}

export function AppShell() {
  const { tenant, logout } = useAuth()
  const { t } = useT()
  const loc = useLocation()
  const [dark, setDark] = useState(document.body.classList.contains('dark'))

  function toggleTheme() {
    const next = !dark
    setDark(next)
    document.body.className = next ? 'dark' : 'light'
    localStorage.setItem('auvex.theme', next ? 'dark' : 'light')
  }

  const tr = t as (k: string) => string
  const initials = (tenant?.name ?? 'A').slice(0, 2).toUpperCase()
  const titleKey = TITLE_KEYS[loc.pathname]
  const title = titleKey
    ? tr(titleKey)
    : loc.pathname.startsWith('/audit/')
      ? 'Request detail'
      : loc.pathname.startsWith('/policies')
        ? 'Policy editor'
        : 'Auvex'

  return (
    <div className="app">
      <a href="#main" className="skip-link">
        Skip to content
      </a>
      <nav className="side" aria-label="Primary">
        <div className="brand">
          <div className="logo">
            <i className="ti ti-shield-lock" aria-hidden />
          </div>
          <span style={{ fontSize: 17, fontWeight: 600 }}>Auvex</span>
        </div>
        {NAV.map((group) => (
          <div key={group.section}>
            <div className="nav-section">{tr(group.section)}</div>
            {group.items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) => `navbtn${isActive ? ' active' : ''}`}
              >
                <i className={`ti ${item.icon}`} aria-hidden />
                <span>{tr(item.label)}</span>
              </NavLink>
            ))}
          </div>
        ))}
      </nav>
      <div className="main">
        <div className="top">
          <span style={{ fontSize: 15, fontWeight: 600 }}>{title}</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <LanguageSwitcher />
            <button className="themebtn" onClick={toggleTheme}>
              <i className={`ti ${dark ? 'ti-sun' : 'ti-moon'}`} aria-hidden />
              <span>{dark ? tr('common.light') : tr('common.dark')}</span>
            </button>
            <button
              className="themebtn"
              onClick={logout}
              title={tr('common.signOut')}
              aria-label={tr('common.signOut')}
            >
              <i className="ti ti-logout-2" aria-hidden />
            </button>
            <div className="avatar" title={tenant?.name}>
              {initials}
            </div>
          </div>
        </div>
        <main className="content anim" id="main" key={loc.pathname}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
