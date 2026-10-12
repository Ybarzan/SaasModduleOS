import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import ClientDashboard from '../pages/client/ClientDashboard'

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }))

vi.mock('../services/api', () => ({
  default: { get: mockGet }
}))

vi.mock('react-router-dom', () => ({
  Link: ({ to, children }) => <a href={to}>{children}</a>,
  useNavigate: () => vi.fn()
}))

describe('ClientDashboard page', () => {
  beforeEach(() => {
    mockGet.mockReset()
    localStorage.clear()
  })

  it('shows the client name and shipment stats', async () => {
    localStorage.setItem(
      'fh_client_user',
      JSON.stringify({ fullName: 'Client Test', email: 'client@ex.com' })
    )
    mockGet.mockResolvedValue({
      data: [
        {
          id: 's1',
          orderNumber: 'EXP-001',
          status: 'IN_TRANSIT',
          shipperCity: 'Paris',
          consigneeCity: 'Berlin'
        }
      ]
    })
    render(<ClientDashboard />)

    expect(await screen.findByText('EXP-001')).toBeInTheDocument()
    expect(screen.getByText('Client Test')).toBeInTheDocument()
  })

  it('shows an empty-state message when there are no shipments', async () => {
    mockGet.mockResolvedValue({ data: [] })
    render(<ClientDashboard />)

    expect(await screen.findByText('Aucune expédition partagée pour le moment')).toBeInTheDocument()
  })
})
