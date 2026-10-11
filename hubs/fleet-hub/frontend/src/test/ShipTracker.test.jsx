import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import ShipTracker from '../pages/ShipTracker'

vi.mock('../services/api', () => ({
  default: { get: vi.fn().mockResolvedValue({ data: [] }) }
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

describe('ShipTracker page', () => {
  it('renders the page heading', async () => {
    render(<ShipTracker />)
    expect(await screen.findByText(/Ship Tracker/)).toBeInTheDocument()
  })
})
