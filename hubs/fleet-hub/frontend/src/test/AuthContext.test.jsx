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

  it('logs in and stores the user (token is in HttpOnly cookie on web)', async () => {
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
