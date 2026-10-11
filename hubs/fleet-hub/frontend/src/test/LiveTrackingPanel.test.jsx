import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import LiveTrackingPanel from '../components/LiveTrackingPanel'

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }))

vi.mock('../services/api', () => ({
  default: { get: mockGet, post: vi.fn() }
}))

describe('LiveTrackingPanel', () => {
  it('shows tracking updates for a shipment', async () => {
    mockGet.mockResolvedValue({
      data: [{ status: 'DEPARTED', eventTime: '2026-01-01T00:00:00Z', location: 'Le Havre' }]
    })
    render(<LiveTrackingPanel shipmentId="ship-1" mode="MARITIME" />)

    expect(await screen.findByText('DEPARTED')).toBeInTheDocument()
    expect(mockGet).toHaveBeenCalledWith('/tracking/shipments/ship-1')
  })

  it('shows an empty-state message when there is no tracking data', async () => {
    mockGet.mockResolvedValue({ data: [] })
    render(<LiveTrackingPanel shipmentId="ship-2" />)

    expect(await screen.findByText(/Aucune donnée de suivi disponible/)).toBeInTheDocument()
  })
})
