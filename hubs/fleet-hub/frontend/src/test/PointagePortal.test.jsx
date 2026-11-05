import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PointagePortal from '../pages/pointage/PointagePortal'

const { mockLogin, mockGet } = vi.hoisted(() => ({
  mockLogin: vi.fn(),
  mockGet: vi.fn()
}))

vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
  useParams: () => ({ accessCode: 'ABCD2345' })
}))
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: null, login: mockLogin, logout: vi.fn() })
}))
vi.mock('../services/api', () => ({
  default: { get: mockGet, post: vi.fn() }
}))

const driver = (pinLength) => ({
  id: 7,
  firstName: 'Jean',
  lastName: 'Dupont',
  loginUsername: 'chauffeur-7@pointage.internal',
  pinLength
})

async function typePin(user, digits) {
  for (const d of digits) {
    await user.click(screen.getByRole('button', { name: d }))
  }
}

describe('PointagePortal PIN pad', () => {
  beforeEach(() => {
    mockLogin.mockReset()
    mockGet.mockReset()
  })

  it('waits for 6 digits before submitting a recent PIN', async () => {
    mockGet.mockResolvedValue({ data: { companyName: 'Démo', drivers: [driver(6)] } })
    mockLogin.mockResolvedValue({})
    const user = userEvent.setup()
    render(<PointagePortal />)

    await user.click(await screen.findByText(/Jean/))
    expect(screen.getByText('Code à 6 chiffres')).toBeInTheDocument()

    await typePin(user, '48151')
    expect(mockLogin).not.toHaveBeenCalled()
    await typePin(user, '6')
    expect(mockLogin).toHaveBeenCalledWith('chauffeur-7@pointage.internal', '481516')
  })

  it('keeps accepting 4-digit PINs created before the switch', async () => {
    mockGet.mockResolvedValue({ data: { companyName: 'Démo', drivers: [driver(4)] } })
    mockLogin.mockResolvedValue({})
    const user = userEvent.setup()
    render(<PointagePortal />)

    await user.click(await screen.findByText(/Jean/))
    await typePin(user, '4815')
    expect(mockLogin).toHaveBeenCalledWith('chauffeur-7@pointage.internal', '4815')
  })

  it('shows the lockout message instead of "code incorrect"', async () => {
    mockGet.mockResolvedValue({ data: { companyName: 'Démo', drivers: [driver(6)] } })
    mockLogin.mockRejectedValue({
      response: { status: 429, data: { message: 'Compte temporairement verrouillé' } }
    })
    const user = userEvent.setup()
    render(<PointagePortal />)

    await user.click(await screen.findByText(/Jean/))
    await typePin(user, '481516')
    expect(await screen.findByText('Compte temporairement verrouillé')).toBeInTheDocument()
  })
})
