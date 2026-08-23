import { createContext, useContext, useState, useCallback, useEffect, useRef } from 'react'
import { Capacitor } from '@capacitor/core'
import api from '../services/api'

const AuthContext = createContext(null)
const REFRESH_INTERVAL_MS = 20 * 60 * 1000 // 20 minutes

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem('fh_user') || 'null')
    } catch {
      return null
    }
  })
  const refreshTimer = useRef(null)

  const startRefreshTimer = useCallback(() => {
    clearInterval(refreshTimer.current)
    refreshTimer.current = setInterval(() => {
      api.post('/auth/refresh').catch(() => {})
    }, REFRESH_INTERVAL_MS)
  }, [])

  useEffect(() => {
    if (user) startRefreshTimer()
    return () => clearInterval(refreshTimer.current)
  }, [user, startRefreshTimer])

  const login = useCallback(async (username, password, totpCode) => {
    const payload = { username, password }
    if (totpCode) payload.totpCode = totpCode
    const res = await api.post('/auth/login', payload)
    // 2FA requis : pas de token, retourner la réponse pour afficher le formulaire TOTP
    if (res.data.totpRequired) {
      return res.data
    }
    if (Capacitor.isNativePlatform()) localStorage.setItem('fh_token', res.data.token)
    localStorage.setItem('fh_user', JSON.stringify(res.data))
    setUser(res.data)
    return res.data
  }, [])

  const register = useCallback(async (payload) => {
    const res = await api.post('/auth/register', payload)
    if (Capacitor.isNativePlatform()) localStorage.setItem('fh_token', res.data.token)
    localStorage.setItem('fh_user', JSON.stringify(res.data))
    setUser(res.data)
    return res.data
  }, [])

  const logout = useCallback(async () => {
    clearInterval(refreshTimer.current)
    try { await api.post('/auth/logout') } catch { /* best effort */ }
    localStorage.removeItem('fh_token')
    localStorage.removeItem('fh_user')
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
