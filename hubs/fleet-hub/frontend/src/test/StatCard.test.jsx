import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import StatCard from '../components/StatCard'

describe('StatCard', () => {
  it('renders label, value and unit', () => {
    render(<StatCard label="Distance totale" value={1200} unit="km" />)
    expect(screen.getByText('Distance totale')).toBeInTheDocument()
    expect(screen.getByText('1200')).toBeInTheDocument()
    expect(screen.getByText('km')).toBeInTheDocument()
  })

  it('applies the tone class', () => {
    render(<StatCard label="Score" value={85} tone="success" />)
    expect(screen.getByText('85').parentElement.closest('.stat-card')).toHaveClass('success')
  })
})
