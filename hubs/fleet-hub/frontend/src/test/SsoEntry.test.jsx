import { render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import SsoEntry from '../pages/SsoEntry'

const { mockLoginWithSession, mockNavigate } = vi.hoisted(() => ({
  mockLoginWithSession: vi.fn(),
  mockNavigate: vi.fn()
}))

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate
}))
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ loginWithSession: mockLoginWithSession })
}))

function encodeSsoPayload(payload) {
  const json = JSON.stringify(payload)
  const base64 = btoa(unescape(encodeURIComponent(json)))
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

describe('SsoEntry page', () => {
  beforeEach(() => {
    mockLoginWithSession.mockClear()
    mockNavigate.mockClear()
    window.location.hash = ''
  })

  it('decodes the fragment, logs the session in, and redirects to /', async () => {
    const payload = { token: 'jwt-abc', username: 'ops', role: 'ADMIN' }
    window.location.hash = '#' + encodeSsoPayload(payload)

    render(<SsoEntry />)

    await waitFor(() => expect(mockLoginWithSession).toHaveBeenCalledWith(payload))
    expect(mockNavigate).toHaveBeenCalledWith('/', { replace: true })
  })

  it('redirects SAAS_ADMIN accounts to /admin instead of /', async () => {
    const payload = { token: 'jwt-abc', username: 'root', role: 'SAAS_ADMIN' }
    window.location.hash = '#' + encodeSsoPayload(payload)

    render(<SsoEntry />)

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/admin', { replace: true }))
  })

  it('shows an error and does not navigate when the fragment is invalid', async () => {
    window.location.hash = '#not-valid-base64!!!'

    render(<SsoEntry />)

    expect(await screen.findByText(/Lien de connexion invalide/)).toBeInTheDocument()
    expect(mockNavigate).not.toHaveBeenCalled()
    expect(mockLoginWithSession).not.toHaveBeenCalled()
  })
})
