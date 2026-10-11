import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import OptimizationDashboard from '../pages/OptimizationDashboard'

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }))

vi.mock('../services/api', () => ({
  default: { get: mockGet, post: vi.fn(), patch: vi.fn() }
}))

describe('OptimizationDashboard page', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockGet.mockResolvedValue({ data: [] })
  })

  it('renders the page heading and empty states', async () => {
    render(<OptimizationDashboard />)
    expect(await screen.findByText(/Moteur d'optimisation tarifaire/)).toBeInTheDocument()
    expect(await screen.findByText(/Aucune lane analysée/)).toBeInTheDocument()
    expect(await screen.findByText(/Aucune recommandation disponible/)).toBeInTheDocument()
  })
})
