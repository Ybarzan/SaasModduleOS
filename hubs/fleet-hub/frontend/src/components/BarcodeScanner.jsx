import { useEffect, useRef, useState } from 'react'

/** Scan de codes-barres / QR via la caméra (API BarcodeDetector : Chrome Android, Edge…). */
export const scanSupported = () => typeof window !== 'undefined' && 'BarcodeDetector' in window

const FORMATS = ['code_128', 'code_39', 'ean_13', 'ean_8', 'qr_code', 'data_matrix', 'itf']

export default function BarcodeScanner({ onCode, onClose }) {
  const videoRef = useRef(null)
  const [error, setError] = useState('')
  const [last, setLast] = useState('')

  useEffect(() => {
    let stream
    let timer
    let stopped = false
    const seen = new Set()

    const start = async () => {
      try {
        const supported = await window.BarcodeDetector.getSupportedFormats()
        const detector = new window.BarcodeDetector({
          formats: FORMATS.filter((f) => supported.includes(f))
        })
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
        if (stopped) return
        videoRef.current.srcObject = stream
        await videoRef.current.play()
        const tick = async () => {
          if (stopped) return
          try {
            const codes = await detector.detect(videoRef.current)
            for (const c of codes) {
              if (!seen.has(c.rawValue)) {
                seen.add(c.rawValue)
                setLast(c.rawValue)
                window.__fhHaptics?.light()
                onCode(c.rawValue)
              }
            }
          } catch {
            /* image pas encore prête */
          }
          timer = setTimeout(tick, 350)
        }
        tick()
      } catch {
        setError("Caméra indisponible : autorisez l'accès ou saisissez les codes à la main.")
      }
    }
    start()
    return () => {
      stopped = true
      clearTimeout(timer)
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [onCode])

  return (
    <div className="scanner">
      {error ? (
        <div className="alert alert-error">{error}</div>
      ) : (
        <video ref={videoRef} className="scanner-video" muted playsInline />
      )}
      <div className="scanner-foot">
        <span className="muted">
          {last ? `Dernier code : ${last}` : 'Visez le code-barres du sachet ou du colis'}
        </span>
        <button type="button" className="btn btn-outline btn-sm" onClick={onClose}>
          Terminer le scan
        </button>
      </div>
    </div>
  )
}
