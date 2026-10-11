import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import SharedLinks from '../pages/SharedLinks'

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }))

vi.mock('../services/api', () => ({
  default: { get: mockGet, post: vi.fn(), delete: vi.fn() }
}))

describe('SharedLinks page', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockGet.mockResolvedValue({ data: [] })
  })

  it('renders the page heading and empty state', async () => {
    render(<SharedLinks />)
    expect(await screen.findByText('Liens de suivi')).toBeInTheDocument()
    expect(await screen.findByText('Aucun lien partagé')).toBeInTheDocument()
  })
})
