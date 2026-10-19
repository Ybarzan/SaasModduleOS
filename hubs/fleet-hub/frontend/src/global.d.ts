export {}

declare global {
  interface Window {
    __fhHaptics?: {
      light: () => void
      medium: () => void
      success: () => void
    }
  }
}
