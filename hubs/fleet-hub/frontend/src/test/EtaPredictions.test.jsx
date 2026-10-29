import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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

  const basePrediction = {
    id: 'p1',
    origin: 'CN',
    destination: 'DE',
    mode: 'SEA',
    carrierName: 'DHL',
    predictedArrival: '2026-11-15T00:00:00',
    predictedDays: 40,
    baselineDays: 40,
    varianceDays: 0,
    confidencePercent: 91,
    confidenceLevel: 'HIGH',
    seasonalFactor: 1,
    congestionFactor: 1,
    customsDelayDays: 0,
    weatherDelayDays: 0
  }

  it('shows the measured-reliability note with sample count when reliabilityWeighted is true', async () => {
    mockGet.mockImplementation((url) =>
      url === '/eta-predictions'
        ? Promise.resolve({
            data: [{ ...basePrediction, reliabilityWeighted: true, reliabilitySampleCount: 42 }]
          })
        : Promise.resolve({ data: {} })
    )

    render(<EtaPredictions />)
    await screen.findByText('Dernière prédiction')
    await userEvent.click(screen.getByText('↓ Voir le détail technique'))

    expect(
      await screen.findByText((text) => text.includes('Fiabilité mesurée sur'))
    ).toBeInTheDocument()
    expect(screen.getByText('42')).toBeInTheDocument()
  })

  it('shows the heuristic-fallback note when reliabilityWeighted is false', async () => {
    mockGet.mockImplementation((url) =>
      url === '/eta-predictions'
        ? Promise.resolve({ data: [{ ...basePrediction, reliabilityWeighted: false }] })
        : Promise.resolve({ data: {} })
    )

    render(<EtaPredictions />)
    await screen.findByText('Dernière prédiction')
    await userEvent.click(screen.getByText('↓ Voir le détail technique'))

    expect(
      await screen.findByText((text) => text.includes('Estimation heuristique'))
    ).toBeInTheDocument()
  })
})
