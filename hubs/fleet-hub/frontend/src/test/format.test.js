import { describe, expect, it } from 'vitest'
import { fmtNum } from '../format'

// toLocaleString('fr-FR') sépare les milliers par une espace fine insécable (U+202F).
const plain = (s) => s.replace(new RegExp('[\\u202f\\u00a0]', 'g'), ' ')

describe('fmtNum', () => {
  it('écrit la virgule décimale française', () => {
    expect(fmtNum(0.96, 2)).toBe('0,96')
    expect(fmtNum(53.2, 1)).toBe('53,2')
  })

  it('garde le nombre de décimales demandé et sépare les milliers', () => {
    expect(fmtNum(30, 1)).toBe('30,0')
    expect(plain(fmtNum(1234.5, 2))).toBe('1 234,50')
    expect(fmtNum(81.6)).toBe('82')
  })

  it('rend une chaîne vide pour une valeur absente', () => {
    expect(fmtNum(null, 2)).toBe('')
    expect(fmtNum(undefined, 2)).toBe('')
    expect(fmtNum('abc', 2)).toBe('')
  })
})
