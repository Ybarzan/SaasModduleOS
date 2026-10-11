import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import GroupagePage from '../pages/GroupagePage'

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }))

vi.mock('../services/api', () => ({
  default: { get: mockGet, post: vi.fn(), delete: vi.fn() }
}))

describe('GroupagePage', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockGet.mockResolvedValue({ data: [] })
  })

  it('renders the page heading and empty state', async () => {
    render(<GroupagePage />)
    expect(await screen.findByText(/Groupage & co-loading/)).toBeInTheDocument()
    expect(await screen.findByText(/Aucun groupage/)).toBeInTheDocument()
  })
})
