import { useEffect, useRef, useState } from 'react'

/** Garde l'écran allumé pendant la tournée (API Wake Lock, si disponible). */
export function useWakeLock(active) {
  useEffect(() => {
    if (!active || !('wakeLock' in window.navigator)) return undefined
    let lock = null
    let cancelled = false
    const acquire = async () => {
      try {
        lock = await window.navigator.wakeLock.request('screen')
      } catch {
        /* refusé (batterie faible, onglet masqué) : sans gravité */
      }
    }
    const onVisible = () => {
      if (!cancelled && document.visibilityState === 'visible') acquire()
    }
    acquire()
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      cancelled = true
      document.removeEventListener('visibilitychange', onVisible)
      lock?.release().catch(() => {})
    }
  }, [active])
}

const toRad = (d) => (d * Math.PI) / 180
export function distanceMeters(lat1, lon1, lat2, lon2) {
  const dLat = toRad(lat2 - lat1)
  const dLon = toRad(lon2 - lon1)
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2
  return 2 * 6371000 * Math.asin(Math.sqrt(a))
}

/**
 * Détection d'arrivée : quand le téléphone entre dans un rayon de `radius` mètres
 * autour de la cible, `onArrive` est appelé une seule fois pour cette cible.
 * La position n'est suivie que tant que `target` est défini (tournée en cours).
 */
export function useGeofence(target, onArrive, { enabled = true, radius = 120 } = {}) {
  const [state, setState] = useState({ distance: null, error: null })
  const firedFor = useRef(null)
  const callback = useRef(onArrive)
  useEffect(() => {
    callback.current = onArrive
  }, [onArrive])

  const key = target ? `${target.id}` : null
  const lat = target?.latitude
  const lon = target?.longitude

  const active =
    enabled && key != null && lat != null && lon != null && 'geolocation' in window.navigator

  useEffect(() => {
    if (!active) return undefined
    const watchId = window.navigator.geolocation.watchPosition(
      (pos) => {
        const d = distanceMeters(pos.coords.latitude, pos.coords.longitude, lat, lon)
        setState({ distance: Math.round(d), error: null })
        if (d <= radius && pos.coords.accuracy <= 150 && firedFor.current !== key) {
          firedFor.current = key
          callback.current()
        }
      },
      (err) => setState({ distance: null, error: err.code === 1 ? 'refusee' : 'indisponible' }),
      { enableHighAccuracy: true, maximumAge: 10000, timeout: 30000 }
    )
    return () => window.navigator.geolocation.clearWatch(watchId)
  }, [active, key, lat, lon, radius])

  return active ? state : { distance: null, error: null }
}

/** Préférence persistée (stockage local, avec repli silencieux). */
export function usePref(key, initial) {
  const [value, setValue] = useState(() => {
    try {
      const v = localStorage.getItem(key)
      return v == null ? initial : JSON.parse(v)
    } catch {
      return initial
    }
  })
  const update = (v) => {
    setValue(v)
    try {
      localStorage.setItem(key, JSON.stringify(v))
    } catch {
      /* préférence non mémorisée */
    }
  }
  return [value, update]
}
