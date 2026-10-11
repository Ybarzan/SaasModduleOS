import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import BrandingPortal from '../pages/BrandingPortal'

vi.mock('../services/api', () => ({
  default: { get: vi.fn().mockResolvedValue({ data: {} }), put: vi.fn(), post: vi.fn() }
}))

describe('BrandingPortal page', () => {
  it('renders the page heading and the live preview', async () => {
    render(<BrandingPortal />)
    expect(await screen.findByText('Portail white-label')).toBeInTheDocument()
    expect(screen.getAllByText(/Aperçu/)[0]).toBeInTheDocument()
  })
})
