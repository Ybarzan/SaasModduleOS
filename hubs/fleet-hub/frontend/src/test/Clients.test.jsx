import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import Clients from '../pages/Clients'

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }))

vi.mock('../services/api', () => ({
  default: { get: mockGet, post: vi.fn(), put: vi.fn(), delete: vi.fn() }
}))

describe('Clients page', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockGet.mockResolvedValue({ data: [] })
  })

  it('renders the page heading and empty state', async () => {
    render(<Clients />)
    expect(await screen.findByText('Clients')).toBeInTheDocument()
    expect(await screen.findByText('Aucun client')).toBeInTheDocument()
  })
})
