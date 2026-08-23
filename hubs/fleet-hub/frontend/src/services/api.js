import axios from 'axios'
import { Capacitor } from '@capacitor/core'

/**
 * URL de base de l'API (inclut le préfixe /api).
 * Ordre de priorité :
 *  1. localStorage `fh_api_base` (surcharge au runtime, utile en test natif)
 *  2. VITE_API_BASE_URL définie au build (production native)
 *  3. Web (navigateur dev) : proxy Vite vers le backend → `/api`
 *  4. Défaut natif documenté (à remplacer par l'URL de production réelle)
 */
export function resolveApiBase() {
  const override = typeof localStorage !== 'undefined' ? localStorage.getItem('fh_api_base') : null
  if (override && !override.startsWith('/')) return override
  if (Capacitor.isNativePlatform()) {
    return import.meta.env.VITE_API_BASE_URL || '/api'
  }
  return '/api'
}

const api = axios.create({ baseURL: resolveApiBase() })

api.interceptors.request.use((config) => {
  const isPublic =
    config.url.includes('/auth/login') ||
    config.url.includes('/auth/register') ||
    config.url.includes('/auth/accept-invitation')
  if (isPublic) return config

  // Web: cookie is sent automatically by the browser (credentials: 'include').
  // Native (Capacitor): read token from localStorage and set Authorization header.
  if (Capacitor.isNativePlatform()) {
    const token = localStorage.getItem('fh_token')
    if (token) config.headers.Authorization = `Bearer ${token}`
  }
  config.withCredentials = true
  return config
})

api.interceptors.response.use(
  (res) => res,
  async (err) => {
    const originalRequest = err.config
    const url = originalRequest?.url || ''
    const isAuthEndpoint = url.includes('/auth/login') ||
      url.includes('/auth/register') ||
      url.includes('/auth/accept-invitation') ||
      url.includes('/auth/forgot-password') ||
      url.includes('/auth/reset-password')
    const isRefreshRequest = url.includes('/auth/refresh')

    if (err.response && err.response.status === 401 && !originalRequest._retry && !isRefreshRequest && !isAuthEndpoint) {
      originalRequest._retry = true
      try {
        await api.post('/auth/refresh')
        return api(originalRequest)
      } catch {
        localStorage.removeItem('fh_user')
        if (!window.location.pathname.startsWith('/login')) {
          window.location.href = '/login'
        }
      }
    } else if (err.response && err.response.status === 401 && isRefreshRequest) {
      localStorage.removeItem('fh_user')
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    }

    if (err.response && err.response.status === 402) {
      if (!window.location.pathname.startsWith('/billing')) {
        window.location.href = '/billing'
      }
    }
    return Promise.reject(err)
  }
)

export default api
