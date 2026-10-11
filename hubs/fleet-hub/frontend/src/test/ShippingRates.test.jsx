import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import ShippingRates from '../pages/ShippingRates'

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }))

vi.mock('../services/api', () => ({
  default: { get: mockGet, post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }
}))

describe('ShippingRates page', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockGet.mockResolvedValue({ data: [] })
  })

  it('renders the page heading and empty state', async () => {
    render(
      <MemoryRouter>
        <ShippingRates />
      </MemoryRouter>
    )
    expect(await screen.findByText('Gestion des tarifs')).toBeInTheDocument()
    expect(await screen.findByText('Aucun tarif configuré')).toBeInTheDocument()
  })
})
