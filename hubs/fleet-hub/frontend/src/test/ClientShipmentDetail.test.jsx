import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import ClientShipmentDetail from '../pages/client/ClientShipmentDetail'

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }))

vi.mock('../services/api', () => ({
  default: { get: mockGet }
}))

vi.mock('react-router-dom', () => ({
  Link: ({ to, children }) => <a href={to}>{children}</a>,
  useParams: () => ({ id: 'ship-1' })
}))

describe('ClientShipmentDetail page', () => {
  it('shows the shipment route and tracking events', async () => {
    mockGet.mockResolvedValue({
      data: {
        orderNumber: 'EXP-001',
        status: 'IN_TRANSIT',
        shipperCity: 'Paris',
        consigneeCity: 'Berlin',
        trackingEvents: [{ id: 'e1', status: 'DEPARTED', eventTime: '2026-01-01T00:00:00Z' }]
      }
    })
    render(<ClientShipmentDetail />)

    expect(await screen.findByText('EXP-001')).toBeInTheDocument()
    expect(screen.getByText('DEPARTED')).toBeInTheDocument()
    expect(mockGet).toHaveBeenCalledWith('/client/shipments/ship-1')
  })

  it('shows a not-found message when the shipment cannot be loaded', async () => {
    mockGet.mockRejectedValue(new Error('not found'))
    render(<ClientShipmentDetail />)

    expect(await screen.findByText('Expédition introuvable')).toBeInTheDocument()
  })
})
