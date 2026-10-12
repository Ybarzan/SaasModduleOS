import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import HubIntegration from '../pages/HubIntegration'

const { mockGet, mockPost } = vi.hoisted(() => ({
  mockGet: vi.fn(),
  mockPost: vi.fn()
}))

vi.mock('../services/api', () => ({
  default: { get: mockGet, post: mockPost }
}))

describe('HubIntegration page', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockPost.mockReset()
  })

  it('shows disabled status and lets the admin activate it, revealing the key once', async () => {
    mockGet.mockResolvedValue({
      data: { internalIntegrationEnabled: false, internalIntegrationApiKey: null }
    })
    mockPost.mockResolvedValue({
      data: { internalIntegrationEnabled: true, internalIntegrationApiKey: 'abc123def456' }
    })
    const user = userEvent.setup()
    render(<HubIntegration />)

    expect(await screen.findByText('Désactivée')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: "Activer l'intégration" }))

    expect(mockPost).toHaveBeenCalledWith('/internal-integrations/enable')
    expect(await screen.findByText('abc123def456')).toBeInTheDocument()
    expect(screen.getByText('Active')).toBeInTheDocument()
  })

  it('shows active status without exposing the key when already enabled', async () => {
    mockGet.mockResolvedValue({
      data: { internalIntegrationEnabled: true, internalIntegrationApiKey: null }
    })
    render(<HubIntegration />)

    expect(await screen.findByText('Active')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: "Désactiver l'intégration" })).toBeInTheDocument()
    expect(screen.queryByText("Votre clé d'intégration")).not.toBeInTheDocument()
  })

  it('deactivates the integration after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    mockGet.mockResolvedValue({
      data: { internalIntegrationEnabled: true, internalIntegrationApiKey: null }
    })
    mockPost.mockResolvedValue({
      data: { internalIntegrationEnabled: false, internalIntegrationApiKey: null }
    })
    const user = userEvent.setup()
    render(<HubIntegration />)

    await user.click(await screen.findByRole('button', { name: "Désactiver l'intégration" }))

    expect(mockPost).toHaveBeenCalledWith('/internal-integrations/disable')
    await waitFor(() => expect(screen.getByText('Désactivée')).toBeInTheDocument())
  })

  it('shows an error when the status fails to load', async () => {
    mockGet.mockRejectedValue(new Error('network error'))
    render(<HubIntegration />)

    expect(
      await screen.findByText("Impossible de charger le statut de l'intégration")
    ).toBeInTheDocument()
  })
})
