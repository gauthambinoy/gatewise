import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, getApiKey, setApiKey } from '../lib/api'
import type { Tenant } from '../lib/types'

interface AuthState {
  tenant: Tenant | null
  loading: boolean
  login: (key: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [tenant, setTenant] = useState<Tenant | null>(null)
  const [loading, setLoading] = useState(true)

  // On boot, if a key is stored, validate it by resolving the tenant.
  useEffect(() => {
    if (!getApiKey()) {
      setLoading(false)
      return
    }
    api
      .me()
      .then(setTenant)
      .catch(() => setApiKey(null))
      .finally(() => setLoading(false))
  }, [])

  async function login(key: string) {
    setApiKey(key.trim())
    try {
      setTenant(await api.me())
    } catch (e) {
      setApiKey(null)
      throw e
    }
  }

  function logout() {
    setApiKey(null)
    setTenant(null)
  }

  return (
    <AuthContext.Provider value={{ tenant, loading, login, logout }}>{children}</AuthContext.Provider>
  )
}
