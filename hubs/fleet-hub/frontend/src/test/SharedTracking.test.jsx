import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import SharedTracking from '../pages/client/SharedTracking'

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }))

vi.mock('../services/api', () => ({
  default: { get: mockGet }
}))

vi.mock('react-router-dom', () => ({
  useParams: () => ({ token: 'tok-1' })
}))

describe('SharedTracking page', () => {
  it('shows the shipment status for a valid token', async () => {
    mockGet.mockResolvedValue({
      data: {
        shipment: { status: 'IN_TRANSIT', shipperCity: 'Paris', consigneeCity: 'Berlin' },
        trackingEvents: [],
        companyName: 'Fleet Hub Transport'
      }
    })
    render(<SharedTracking />)

    expect(await screen.findByText('Fleet Hub Transport')).toBeInTheDocument()
    expect(mockGet).toHaveBeenCalledWith('/shared/access/tok-1')
  })

  it('shows an invalid-link message when the token is rejected', async () => {
    mockGet.mockRejectedValue({ response: { data: { message: 'Lien expiré' } } })
    render(<SharedTracking />)

    expect(await screen.findByText('Lien invalide')).toBeInTheDocument()
    expect(screen.getByText('Lien expiré')).toBeInTheDocument()
  })
})
