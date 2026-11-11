export {}

declare global {
  interface Window {
    __fhHaptics?: {
      light: () => void
      medium: () => void
      success: () => void
      heavy?: () => void
    }
    /** API Barcode Detection (Chrome Android) — absente des typings DOM standard. */
    BarcodeDetector?: any
  }
}
