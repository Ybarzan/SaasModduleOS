import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import Logistics from '../pages/Logistics'

vi.mock('../services/api', () => ({
  default: { get: vi.fn(), post: vi.fn() }
}))

describe('Logistics page', () => {
  it('renders the page heading and the add-item form', () => {
    render(<Logistics />)
    expect(screen.getByText(/Dashboard logistique/)).toBeInTheDocument()
    expect(screen.getByPlaceholderText('SKU')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Calculer le packaging/ })).toBeDisabled()
  })
})
