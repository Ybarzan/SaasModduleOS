import { beforeEach, describe, expect, it } from 'vitest'

const { requestHandlers, responseHandlers, instance } = vi.hoisted(() => {
  const requestHandlers = []
  const responseHandlers = []
  const instance = {
    post: vi.fn(),
    get: vi.fn(),
    interceptors: {
      request: { use: (fn) => requestHandlers.push(fn) },
      response: { use: (ok, err) => responseHandlers.push(ok, err) }
    }
  }
  return { requestHandlers, responseHandlers, instance }
})

vi.mock('axios', () => ({
  default: { create: vi.fn(() => instance) }
}))

vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: () => false }
}))

import api from '../services/api' // eslint-disable-line no-unused-vars -- triggers interceptor registration

describe('api client', () => {
  beforeEach(() => {
    localStorage.clear()
    window.location.href = 'http://localhost:5199/'
    window.location.pathname = '/'
  })

  it('sets withCredentials for cookie-based auth on web', () => {
    const out = requestHandlers[0]({ url: '/api/trucks', headers: {} })
    expect(out.withCredentials).toBe(true)
    expect(out.headers.Authorization).toBeUndefined()
  })

  it('does not attach the token or credentials on the login endpoint', () => {
    const out = requestHandlers[0]({ url: '/api/auth/login', headers: {} })
    expect(out.headers.Authorization).toBeUndefined()
  })

  it('clears the user session and redirects on 401 after refresh fails', async () => {
    instance.post.mockRejectedValueOnce(new Error('refresh failed'))
    localStorage.setItem('fh_user', JSON.stringify({ username: 'admin' }))
    window.location.pathname = '/trucks'

    const errHandler = responseHandlers[1]
    const rejected = errHandler({ config: {}, response: { status: 401 } })

    await rejected.catch(() => {})
    expect(localStorage.getItem('fh_user')).toBeNull()
    expect(window.location.href).toBe('/login')
  })

  it('does not redirect when already on the login page', async () => {
    instance.post.mockRejectedValueOnce(new Error('refresh failed'))
    window.location.pathname = '/login'
    const errHandler = responseHandlers[1]
    const rejected = errHandler({ config: {}, response: { status: 401 } })
    await rejected.catch(() => {})
    expect(window.location.href).toBe('http://localhost:5199/')
  })
})
