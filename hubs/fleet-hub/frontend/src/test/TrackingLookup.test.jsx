import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import TrackingLookup from '../pages/TrackingLookup'

const { mockPost } = vi.hoisted(() => ({ mockPost: vi.fn() }))

vi.mock('../services/api', () => ({
  default: { post: mockPost }
}))

describe('TrackingLookup page', () => {
  it('renders the search form and looks up a tracking number', async () => {
    mockPost.mockResolvedValue({
      data: [{ status: 'IN_TRANSIT', eventTime: '2026-01-01T00:00:00Z' }]
    })
    const user = userEvent.setup()
    render(<TrackingLookup />)

    expect(screen.getByText("Suivi d'expédition")).toBeInTheDocument()
    await user.type(screen.getByPlaceholderText(/N° de suivi transporteur/), 'ABC123')
    await user.click(screen.getByRole('button', { name: 'Rechercher' }))

    expect(mockPost).toHaveBeenCalledWith('/tracking/lookup', {
      trackingNumber: 'ABC123',
      mode: 'ROAD'
    })
    expect(await screen.findByText('IN_TRANSIT')).toBeInTheDocument()
  })
})
