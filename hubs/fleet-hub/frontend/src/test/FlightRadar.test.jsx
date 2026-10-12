import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import FlightRadar from '../pages/FlightRadar'

vi.mock('../services/api', () => ({
  default: { get: vi.fn().mockResolvedValue({ data: { states: [] } }) }
}))

vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }) => <div>{children}</div>,
  TileLayer: () => null,
  Marker: ({ children }) => <div>{children}</div>,
  Popup: ({ children }) => <div>{children}</div>,
  useMapEvents: () => ({
    getBounds: () => ({ getSouth: () => 0, getNorth: () => 0, getWest: () => 0, getEast: () => 0 })
  })
}))

vi.mock('leaflet', () => ({
  default: { divIcon: () => ({}) }
}))

describe('FlightRadar page', () => {
  it('renders the page heading', async () => {
    render(<FlightRadar />)
    expect(await screen.findByText(/Flight Radar/)).toBeInTheDocument()
  })
})
