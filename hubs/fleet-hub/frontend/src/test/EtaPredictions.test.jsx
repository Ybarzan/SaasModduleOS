import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import EtaPredictions from '../pages/EtaPredictions'

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }))

vi.mock('../services/api', () => ({
  default: { get: mockGet, post: vi.fn(), put: vi.fn() }
}))

describe('EtaPredictions page', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockGet.mockResolvedValue({ data: [] })
  })

  it('renders the page heading and empty state', async () => {
    render(<EtaPredictions />)
    expect(await screen.findByText('Prédictions ETA')).toBeInTheDocument()
    expect(await screen.findByText('Aucune prédiction pour le moment')).toBeInTheDocument()
  })
})
