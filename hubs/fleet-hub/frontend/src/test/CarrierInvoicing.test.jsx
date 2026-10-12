import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import CarrierInvoicing from '../pages/CarrierInvoicing'

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }))

vi.mock('../services/api', () => ({
  default: { get: mockGet, post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }
}))

describe('CarrierInvoicing page', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockGet.mockResolvedValue({ data: [] })
  })

  it('renders the page heading and empty state', async () => {
    render(
      <MemoryRouter>
        <CarrierInvoicing />
      </MemoryRouter>
    )
    expect(await screen.findByText('Facturation transporteurs')).toBeInTheDocument()
    expect((await screen.findAllByText('Aucune facture'))[0]).toBeInTheDocument()
  })
})
