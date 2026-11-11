import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import HubIntegration from '../pages/HubIntegration'

const { mockGet, mockPost, mockDelete } = vi.hoisted(() => ({
  mockGet: vi.fn(),
  mockPost: vi.fn(),
  mockDelete: vi.fn()
}))

vi.mock('../services/api', () => ({
  default: { get: mockGet, post: mockPost, delete: mockDelete }
}))

const key = (consumer, label, configured, extra = {}) => ({
  consumer,
  label,
  scopes: ['SHIPMENTS_READ'],
  configured,
  keyHint: configured ? 'f00d' : null,
  createdAt: null,
  lastUsedAt: null,
  ...extra
})

const settings = (enabled, keys) => ({ data: { internalIntegrationEnabled: enabled, keys } })

const row = (label) => screen.getByText(label).closest('tr')

describe('HubIntegration page', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockPost.mockReset()
    mockDelete.mockReset()
    vi.restoreAllMocks()
  })

  it('activates the sharing switch', async () => {
    mockGet.mockResolvedValue(settings(false, [key('LOGISTIX', 'Logistix', false)]))
    mockPost.mockResolvedValue(settings(true, [key('LOGISTIX', 'Logistix', false)]))
    const user = userEvent.setup()
    render(<HubIntegration />)

    expect(await screen.findByText('Désactivée')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: "Activer l'intégration" }))

    expect(mockPost).toHaveBeenCalledWith('/internal-integrations/enable')
    expect(await screen.findByText('Active')).toBeInTheDocument()
  })

  it('generates a key for one hub and shows it once with where to paste it', async () => {
    mockGet.mockResolvedValue(settings(true, [key('LOGISTIX', 'Logistix', false)]))
    mockPost.mockResolvedValue({ data: { consumer: 'LOGISTIX', apiKey: 'abc123def456' } })
    const user = userEvent.setup()
    render(<HubIntegration />)

    await screen.findByText('Logistix')
    await user.click(within(row('Logistix')).getByRole('button', { name: 'Générer' }))

    expect(mockPost).toHaveBeenCalledWith('/internal-integrations/keys/LOGISTIX')
    expect(await screen.findByText('abc123def456')).toBeInTheDocument()
    expect(screen.getByText(/Logistix › Paramètres › Intégration Fleet Hub/)).toBeInTheDocument()
  })

  it('asks for confirmation before replacing an existing key', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    mockGet.mockResolvedValue(settings(true, [key('LOGISTIX', 'Logistix', true)]))
    const user = userEvent.setup()
    render(<HubIntegration />)

    await screen.findByText('…f00d')
    await user.click(within(row('Logistix')).getByRole('button', { name: 'Remplacer' }))

    expect(confirm).toHaveBeenCalled()
    expect(mockPost).not.toHaveBeenCalled()
  })

  it('revokes a key after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    mockGet.mockResolvedValue(settings(true, [key('FINANCE', 'Finance', true)]))
    mockDelete.mockResolvedValue(settings(true, [key('FINANCE', 'Finance', false)]))
    const user = userEvent.setup()
    render(<HubIntegration />)

    await screen.findByText('…f00d')
    await user.click(within(row('Finance')).getByRole('button', { name: 'Révoquer' }))

    expect(mockDelete).toHaveBeenCalledWith('/internal-integrations/keys/FINANCE')
    await waitFor(() => expect(screen.getByText('Aucune')).toBeInTheDocument())
  })

  it('warns while the legacy shared key is still active, without offering to regenerate it', async () => {
    mockGet.mockResolvedValue(
      settings(true, [key('LEGACY', 'Clé historique (partagée)', true, { keyHint: null })])
    )
    render(<HubIntegration />)

    expect(
      await screen.findByText(/Une clé historique, partagée par tous les hubs/)
    ).toBeInTheDocument()
    const legacyRow = row('Clé historique (partagée)')
    expect(within(legacyRow).queryByRole('button', { name: /Générer|Remplacer/ })).toBeNull()
    expect(within(legacyRow).getByRole('button', { name: 'Révoquer' })).toBeInTheDocument()
  })

  it('shows an error when the status fails to load', async () => {
    mockGet.mockRejectedValue(new Error('network error'))
    render(<HubIntegration />)

    expect(
      await screen.findByText("Impossible de charger le statut de l'intégration")
    ).toBeInTheDocument()
  })
})
