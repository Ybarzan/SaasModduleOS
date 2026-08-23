import '@testing-library/jest-dom/vitest'
import { vi } from 'vitest'

Object.defineProperty(window, 'location', {
  value: { href: 'http://localhost:5199/', pathname: '/' },
  writable: true
})

vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: () => false }
}))
