import { describe, expect, it } from 'vitest'
import { energyUnit } from '../fleet'

describe('energyUnit', () => {
  it('compte un électrique en kWh et les autres énergies en litres', () => {
    expect(energyUnit('ELECTRIC')).toBe('kWh')
    expect(energyUnit('DIESEL')).toBe('L')
    expect(energyUnit(undefined)).toBe('L')
  })
})
