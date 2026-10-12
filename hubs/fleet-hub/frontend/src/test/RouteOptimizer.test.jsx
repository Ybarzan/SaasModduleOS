import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import RouteOptimizer from '../pages/RouteOptimizer'

vi.mock('../services/api', () => ({
  default: { post: vi.fn() }
}))

vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }) => <div>{children}</div>,
  TileLayer: () => null,
  Marker: ({ children }) => <div>{children}</div>,
  Popup: ({ children }) => <div>{children}</div>,
  Polyline: () => null,
  useMap: () => ({ fitBounds: () => {} })
}))

vi.mock('leaflet', () => ({
  default: { divIcon: () => ({}) }
}))

describe('RouteOptimizer page', () => {
  it('renders the page heading and optimize button', () => {
    render(<RouteOptimizer />)
    expect(screen.getByText(/Optimisation d'itinéraire/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: "Optimiser l'itinéraire" })).toBeInTheDocument()
  })
})
