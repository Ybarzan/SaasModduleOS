import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import Providers from '../pages/Providers'

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }))

vi.mock('../services/api', () => ({
  default: { get: mockGet, post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }
}))

describe('Providers page', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockGet.mockResolvedValue({ data: [] })
  })

  it('renders the three provider definitions', async () => {
    render(
      <MemoryRouter>
        <Providers />
      </MemoryRouter>
    )
    expect(await screen.findByText('Fournisseurs de tarifs')).toBeInTheDocument()
    expect(screen.getByText(/Shippo/)).toBeInTheDocument()
    expect(screen.getByText(/DHL Express/)).toBeInTheDocument()
    expect(screen.getByText(/Tarifs internes/)).toBeInTheDocument()
  })
})
