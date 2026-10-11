import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import Quotes from '../pages/Quotes'

vi.mock('../services/api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }
}))

describe('Quotes page', () => {
  it('renders the page heading and search form', () => {
    render(
      <MemoryRouter>
        <Quotes />
      </MemoryRouter>
    )
    expect(screen.getByText('Comparaison de devis')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Demander des devis' })).toBeInTheDocument()
  })
})
