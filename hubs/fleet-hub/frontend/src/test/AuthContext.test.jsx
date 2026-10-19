import { renderHook, act } from '@testing-library/react'
import { describe, expect, it, beforeEach, vi } from 'vitest'
import { AuthProvider, useAuth } from '../context/AuthContext'
import api from '../services/api'

vi.mock('../services/api', () => ({
  default: { post: vi.fn() }
}))

const wrapper = ({ children }) => <AuthProvider>{children}</AuthProvider>

describe('AuthContext', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('restores the session from localStorage', () => {
    localStorage.setItem('fh_user', JSON.stringify({ username: 'admin', role: 'ADMIN' }))
    const { result } = renderHook(() => useAuth(), { wrapper })
    expect(result.current.user.username).toBe('admin')
  })

  it('logs in and stores the user (token is in HttpOnly cookie on web, never in fh_user)', async () => {
    api.post.mockResolvedValue({
      data: { token: 'jwt-1', username: 'admin', role: 'ADMIN', displayName: 'Admin' }
    })
    const { result } = renderHook(() => useAuth(), { wrapper })

    await act(async () => {
      await result.current.login('admin', 'admin')
    })

    expect(api.post).toHaveBeenCalledWith('/auth/login', { username: 'admin', password: 'admin' })
    expect(result.current.user.username).toBe('admin')
    expect(localStorage.getItem('fh_user')).toContain('"username":"admin"')
    // Le JWT ne doit jamais atterrir dans fh_user (localStorage, lisible par un
    // script XSS) : seul le cookie httpOnly posé par le backend authentifie.
    expect(localStorage.getItem('fh_user')).not.toContain('jwt-1')
    expect(result.current.user.token).toBeUndefined()
  })

  it('loginWithSession exchanges the relayed token for the httpOnly cookie, never storing it', async () => {
    api.post.mockResolvedValue({ status: 200 })
    const { result } = renderHook(() => useAuth(), { wrapper })

    await act(async () => {
      await result.current.loginWithSession({ token: 'jwt-sso', username: 'ops', role: 'ADMIN' })
    })

    expect(api.post).toHaveBeenCalledWith('/auth/session-from-token', { token: 'jwt-sso' })
    expect(result.current.user.username).toBe('ops')
    expect(localStorage.getItem('fh_token')).toBeNull()
    expect(localStorage.getItem('fh_user')).toContain('"username":"ops"')
    expect(localStorage.getItem('fh_user')).not.toContain('jwt-sso')
  })

  it('loginWithSession propagates a failed token exchange without storing any session', async () => {
    api.post.mockRejectedValue(new Error('401'))
    const { result } = renderHook(() => useAuth(), { wrapper })

    await expect(
      act(async () => {
        await result.current.loginWithSession({ token: 'jwt-revoked', username: 'ops', role: 'ADMIN' })
      })
    ).rejects.toThrow()

    expect(result.current.user).toBeNull()
    expect(localStorage.getItem('fh_user')).toBeNull()
  })

  it('logs out and clears the session', async () => {
    localStorage.setItem('fh_user', JSON.stringify({ username: 'admin' }))
    const { result } = renderHook(() => useAuth(), { wrapper })

    await act(async () => {
      await result.current.logout()
    })

    expect(result.current.user).toBeNull()
    expect(localStorage.getItem('fh_user')).toBeNull()
    expect(api.post).toHaveBeenCalledWith('/auth/logout')
  })
})
