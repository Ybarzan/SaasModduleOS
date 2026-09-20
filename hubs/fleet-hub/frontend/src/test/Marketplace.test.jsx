import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import Marketplace from '../pages/Marketplace'

const { mockGet, mockPost } = vi.hoisted(() => ({
  mockGet: vi.fn(),
  mockPost: vi.fn()
}))

vi.mock('../services/api', () => ({
  default: { get: mockGet, post: mockPost }
}))

describe('Marketplace page', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockPost.mockReset()
  })

  it('shows disabled status and lets the admin activate sharing, revealing the key once', async () => {
    mockGet.mockResolvedValue({ data: { marketplaceOptIn: false, marketplaceApiKey: null } })
    mockPost.mockResolvedValue({ data: { marketplaceOptIn: true, marketplaceApiKey: 'abc123def456' } })
    const user = userEvent.setup()
    render(<Marketplace />)

    expect(await screen.findByText('Désactivé')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Activer le partage' }))

    expect(mockPost).toHaveBeenCalledWith('/marketplace/opt-in')
    expect(await screen.findByText('abc123def456')).toBeInTheDocument()
    expect(screen.getByText('Actif')).toBeInTheDocument()
  })

  it('shows active status without exposing the key when already opted in', async () => {
    mockGet.mockResolvedValue({ data: { marketplaceOptIn: true, marketplaceApiKey: null } })
    render(<Marketplace />)

    expect(await screen.findByText('Actif')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Désactiver le partage' })).toBeInTheDocument()
    expect(screen.queryByText('Votre clé FleetMarket')).not.toBeInTheDocument()
  })

  it('deactivates sharing after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    mockGet.mockResolvedValue({ data: { marketplaceOptIn: true, marketplaceApiKey: null } })
    mockPost.mockResolvedValue({ data: { marketplaceOptIn: false, marketplaceApiKey: null } })
    const user = userEvent.setup()
    render(<Marketplace />)

    await user.click(await screen.findByRole('button', { name: 'Désactiver le partage' }))

    expect(mockPost).toHaveBeenCalledWith('/marketplace/opt-out')
    await waitFor(() => expect(screen.getByText('Désactivé')).toBeInTheDocument())
  })

  it('shows an error when the status fails to load', async () => {
    mockGet.mockRejectedValue(new Error('network error'))
    render(<Marketplace />)

    expect(await screen.findByText('Impossible de charger le statut du partage')).toBeInTheDocument()
  })
})
