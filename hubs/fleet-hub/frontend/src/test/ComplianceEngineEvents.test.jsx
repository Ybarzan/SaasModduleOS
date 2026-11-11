import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ComplianceEngineEvents from '../components/ComplianceEngineEvents'

const { mockGet, mockPut, mockPost, mockDelete } = vi.hoisted(() => ({
  mockGet: vi.fn(),
  mockPut: vi.fn(),
  mockPost: vi.fn(),
  mockDelete: vi.fn()
}))

vi.mock('../services/api', () => ({
  default: { get: mockGet, put: mockPut, post: mockPost, delete: mockDelete }
}))

const state = (configured, pendingEvents = 0, failedEvents = 0, lastError = null) => ({
  data: { configured, pendingEvents, failedEvents, lastError }
})

describe('ComplianceEngineEvents', () => {
  beforeEach(() => {
    ;[mockGet, mockPut, mockPost, mockDelete].forEach((m) => m.mockReset())
    vi.restoreAllMocks()
  })

  it('saves a pasted webhook key and clears the field', async () => {
    mockGet.mockResolvedValue(state(false))
    mockPut.mockResolvedValue(state(true))
    const user = userEvent.setup()
    render(<ComplianceEngineEvents />)

    expect(await screen.findByText('Non configuré')).toBeInTheDocument()
    const input = screen.getByLabelText(/Clé webhook Compliance Engine/)
    await user.type(input, ' cle-ce-123 ')
    await user.click(screen.getByRole('button', { name: 'Enregistrer la clé' }))

    expect(mockPut).toHaveBeenCalledWith('/compliance-engine-webhook/settings', {
      webhookKey: 'cle-ce-123'
    })
    expect(await screen.findByText('Actif')).toBeInTheDocument()
    await waitFor(() => expect(input).toHaveValue(''))
  })

  it('shows the queue state and retries abandoned events', async () => {
    mockGet.mockResolvedValue(state(true, 2, 3, 'HTTP 503 : indisponible'))
    mockPost.mockResolvedValue(state(true, 5, 0))
    const user = userEvent.setup()
    render(<ComplianceEngineEvents />)

    expect(await screen.findByText('HTTP 503 : indisponible')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Relancer les événements abandonnés' }))

    expect(mockPost).toHaveBeenCalledWith('/compliance-engine-webhook/retry-failed')
    expect(await screen.findByText('5')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Relancer les événements abandonnés' })).toBeNull()
  })

  it('keeps the key typed when saving fails', async () => {
    mockGet.mockResolvedValue(state(false))
    mockPut.mockRejectedValue({ response: { data: { message: 'La clé webhook est requise' } } })
    const user = userEvent.setup()
    render(<ComplianceEngineEvents />)

    const input = await screen.findByLabelText(/Clé webhook Compliance Engine/)
    await user.type(input, 'x')
    await user.click(screen.getByRole('button', { name: 'Enregistrer la clé' }))

    expect(await screen.findByText('La clé webhook est requise')).toBeInTheDocument()
    expect(input).toHaveValue('x')
  })
})
