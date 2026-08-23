import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import Login from '../pages/Login'

const { mockLogin, mockNavigate } = vi.hoisted(() => ({
  mockLogin: vi.fn(),
  mockNavigate: vi.fn()
}))

vi.mock('react-router-dom', () => ({
  Link: ({ to, children }) => <a href={to}>{children}</a>,
  useNavigate: () => mockNavigate,
  useLocation: () => ({ state: null })
}))
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ login: mockLogin })
}))

describe('Login page', () => {
  it('submits the credentials and navigates home', async () => {
    mockLogin.mockResolvedValue({ username: 'admin', totpRequired: false })
    const user = userEvent.setup()
    render(<Login />)

    await user.type(screen.getByLabelText('Utilisateur'), 'admin')
    await user.type(screen.getByLabelText('Mot de passe'), 'secret')
    await user.click(screen.getByRole('button', { name: /se connecter/i }))

    expect(mockLogin).toHaveBeenCalledWith('admin', 'secret', undefined)
    expect(mockNavigate).toHaveBeenCalledWith('/', { replace: true })
  })

  it('shows an error message when credentials are invalid', async () => {
    mockLogin.mockRejectedValue({ response: { status: 401 } })
    const user = userEvent.setup()
    render(<Login />)

    await user.click(screen.getByRole('button', { name: /se connecter/i }))

    expect(await screen.findByText('Identifiants invalides')).toBeInTheDocument()
  })
})
