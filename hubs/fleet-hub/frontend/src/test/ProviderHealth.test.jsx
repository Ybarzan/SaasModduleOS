import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import ProviderHealth from '../pages/ProviderHealth'

vi.mock('../services/api', () => ({
  default: { get: vi.fn().mockResolvedValue({ data: [] }) }
}))

describe('ProviderHealth page', () => {
  it('renders the page heading and empty state', async () => {
    render(<ProviderHealth />)
    expect(await screen.findByText('État de santé')).toBeInTheDocument()
    expect(await screen.findByText('Aucun fournisseur configuré')).toBeInTheDocument()
  })
})
