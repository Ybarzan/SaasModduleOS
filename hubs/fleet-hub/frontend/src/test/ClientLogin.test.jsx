import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import ClientLogin from '../pages/client/ClientLogin'

const { mockPost, mockNavigate } = vi.hoisted(() => ({ mockPost: vi.fn(), mockNavigate: vi.fn() }))

vi.mock('../services/api', () => ({
  default: { post: mockPost }
}))

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate
}))

describe('ClientLogin page', () => {
  it('logs the client in and stores the client token', async () => {
    mockPost.mockResolvedValue({
      data: {
        token: 'client-jwt',
        clientId: 'c1',
        email: 'client@ex.com',
        fullName: 'Client Test',
        companyId: 'co1'
      }
    })
    const user = userEvent.setup()
    const { container } = render(<ClientLogin />)

    await user.type(screen.getByPlaceholderText('client@entreprise.com'), 'client@ex.com')
    await user.type(container.querySelector('input[type="password"]'), 'secret')
    await user.click(screen.getByRole('button', { name: 'Se connecter' }))

    expect(mockPost).toHaveBeenCalledWith('/client/auth/login', {
      email: 'client@ex.com',
      password: 'secret'
    })
    expect(localStorage.getItem('fh_client_token')).toBe('client-jwt')
    expect(mockNavigate).toHaveBeenCalledWith('/client/dashboard')
  })
})
