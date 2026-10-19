import { createContext, useContext, useState, useCallback, useEffect, useRef } from 'react'
import { Capacitor } from '@capacitor/core'
import api from '../services/api'

const AuthContext = createContext(null)
const REFRESH_INTERVAL_MS = 20 * 60 * 1000 // 20 minutes

// Le JWT n'a jamais sa place dans `fh_user` : l'authentification repose sur le
// cookie httpOnly (ou, sur natif, fh_token) -- jamais lu par du code applicatif
// via `user`. Dupliquer le token ici le rendrait lisible par n'importe quel
// script injecté (XSS), ce que le cookie httpOnly évite précisément.
function stripToken({ token, ...rest }) {
  return rest
}

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

  const persistUser = useCallback((data) => {
    const safeUser = stripToken(data)
    localStorage.setItem('fh_user', JSON.stringify(safeUser))
    setUser(safeUser)
  }, [])

  const login = useCallback(async (username, password, totpCode) => {
    const payload = { username, password }
    if (totpCode) payload.totpCode = totpCode
    const res = await api.post('/auth/login', payload)
    // 2FA requis : pas de token, retourner la réponse pour afficher le formulaire TOTP
    if (res.data.totpRequired) {
      return res.data
    }
    if (Capacitor.isNativePlatform()) localStorage.setItem('fh_token', res.data.token)
    persistUser(res.data)
    return res.data
  }, [])

  // Relais SSO depuis Praxio (voir hubs/portal/backend SsoService + pages/SsoEntry.jsx) :
  // la réponse de connexion a déjà été obtenue côté serveur par Praxio, mais le navigateur
  // n'a jamais reçu le Set-Cookie fh_token (cette connexion a eu lieu entre Praxio et
  // fleet-hub, pas entre ce navigateur et fleet-hub). Sur web, on échange donc ce jeton
  // contre le même cookie httpOnly qu'une connexion directe (voir
  // AuthController.sessionFromToken) plutôt que de le garder en localStorage, où il serait
  // lisible par n'importe quel script injecté (XSS). Sur natif (Capacitor), pas de cookie
  // cross-origin utilisable : on garde le jeton en localStorage comme pour login/register.
  const loginWithSession = useCallback(async (data) => {
    if (Capacitor.isNativePlatform()) {
      localStorage.setItem('fh_token', data.token)
    } else {
      // Échec de l'échange = jeton invalide/expiré/révoqué : remonter l'erreur plutôt que
      // de laisser un état "connecté" sans aucun moyen de s'authentifier auprès de l'API.
      await api.post('/auth/session-from-token', { token: data.token })
    }
    persistUser(data)
  }, [])

  const register = useCallback(async (payload) => {
    const res = await api.post('/auth/register', payload)
    if (Capacitor.isNativePlatform()) localStorage.setItem('fh_token', res.data.token)
    persistUser(res.data)
    return res.data
  }, [])

  const logout = useCallback(async () => {
    clearInterval(refreshTimer.current)
    try {
      await api.post('/auth/logout')
    } catch {
      /* best effort */
    }
    localStorage.removeItem('fh_token')
    localStorage.removeItem('fh_user')
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, login, loginWithSession, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
