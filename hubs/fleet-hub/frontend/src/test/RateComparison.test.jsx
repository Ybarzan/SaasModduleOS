import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import RateComparison from '../pages/RateComparison'

vi.mock('../services/api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }
}))

describe('RateComparison page', () => {
  it('renders the page heading and search form', () => {
    render(
      <MemoryRouter>
        <RateComparison />
      </MemoryRouter>
    )
    expect(screen.getByText('Comparateur de tarifs multi-transporteurs')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Comparer les tarifs' })).toBeInTheDocument()
  })
})
