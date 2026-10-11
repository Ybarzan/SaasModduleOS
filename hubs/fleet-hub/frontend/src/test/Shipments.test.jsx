import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import Shipments from '../pages/Shipments'

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }))

vi.mock('../services/api', () => ({
  default: { get: mockGet, post: vi.fn(), patch: vi.fn(), delete: vi.fn() }
}))

describe('Shipments page', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockGet.mockResolvedValue({ data: [] })
  })

  it('renders the page heading and an empty state when there are no shipments', async () => {
    render(<Shipments />)
    expect(await screen.findByText('Expéditions')).toBeInTheDocument()
    expect(
      await screen.findByText('Créez votre première expédition pour commencer')
    ).toBeInTheDocument()
  })

  it('lists a shipment returned by the API', async () => {
    mockGet.mockResolvedValue({
      data: [
        {
          id: 's1',
          orderNumber: 'EXP-2026-0001',
          status: 'DRAFT',
          shipperCity: 'Paris',
          consigneeCity: 'Berlin',
          createdAt: '2026-01-01T00:00:00Z'
        }
      ]
    })
    render(<Shipments />)

    expect(await screen.findByText('EXP-2026-0001')).toBeInTheDocument()
  })
})
