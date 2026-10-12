import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import Carriers from '../pages/Carriers'

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }))

vi.mock('../services/api', () => ({
  default: { get: mockGet, post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }
}))

describe('Carriers page', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockGet.mockResolvedValue({ data: [] })
  })

  it('renders the page heading and empty state', async () => {
    render(
      <MemoryRouter>
        <Carriers />
      </MemoryRouter>
    )
    expect(await screen.findByText('Transporteurs')).toBeInTheDocument()
    expect(
      await screen.findByText('Aucun transporteur ne correspond à la recherche')
    ).toBeInTheDocument()
  })

  it('lists a carrier returned by the API', async () => {
    mockGet.mockResolvedValue({
      data: [
        {
          id: '1',
          name: 'DHL Express',
          code: 'DHL',
          transportModes: 'ROAD,AIR',
          active: true,
          country: 'France'
        }
      ]
    })
    render(
      <MemoryRouter>
        <Carriers />
      </MemoryRouter>
    )
    expect((await screen.findAllByText('DHL Express'))[0]).toBeInTheDocument()
  })
})
