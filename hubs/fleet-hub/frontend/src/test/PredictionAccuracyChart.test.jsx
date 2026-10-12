import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import PredictionAccuracyChart from '../components/PredictionAccuracyChart'

vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }) => <div data-testid="chart">{children}</div>,
  BarChart: ({ children }) => <div>{children}</div>,
  Bar: () => null,
  CartesianGrid: () => null,
  Legend: () => null,
  Tooltip: () => null,
  XAxis: () => null,
  YAxis: () => null
}))

describe('PredictionAccuracyChart', () => {
  it('renders without crashing given accuracy data points', () => {
    render(<PredictionAccuracyChart data={[{ label: 'FR→DE', predicted: 5, actual: 6 }]} />)
    expect(screen.getByTestId('chart')).toBeInTheDocument()
  })
})
