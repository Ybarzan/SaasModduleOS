import api from '../../services/api'

/**
 * File d'attente des actions du chauffeur (arrivée, clôture d'arrêt).
 * - Chaque action est horodatée sur le téléphone (occurredAt) et conservée
 *   dans le stockage local : rien n'est perdu en zone blanche ou en sous-sol.
 * - Envoi dans l'ordre, dès que le réseau le permet (reprise automatique).
 * - Délai d'annulation : une action peut être annulée tant qu'elle n'est pas partie.
 */

const KEY = 'fh_driver_queue'
const UNDO_DELAY_MS = 5000
const RETRY_MS = 15000

let items = read()
let flushing = false
const listeners = new Set()

function read() {
  try {
    return JSON.parse(localStorage.getItem(KEY) || '[]')
  } catch {
    return []
  }
}

function persist() {
  try {
    localStorage.setItem(KEY, JSON.stringify(items))
  } catch {
    /* stockage indisponible : la file reste en mémoire */
  }
}

function emit(event) {
  listeners.forEach((l) => l({ ...event, pending: items.length }))
}

/** Heure locale du téléphone au format LocalDateTime (sans fuseau), attendu par l'API. */
export function localNow() {
  const d = new Date()
  const off = d.getTimezoneOffset()
  return new Date(d.getTime() - off * 60000).toISOString().slice(0, 19)
}

export function pendingCount() {
  return items.length
}

export function pendingFor(stopId) {
  return items.filter((i) => i.stopId === stopId)
}

/** Ajoute une action ; renvoie son identifiant (pour l'annuler pendant le délai). */
export function enqueue({ url, body, stopId, kind, undoable = true }) {
  const item = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    url,
    body: { ...body, occurredAt: localNow() },
    stopId,
    kind,
    notBefore: undoable ? Date.now() + UNDO_DELAY_MS : 0
  }
  items.push(item)
  persist()
  emit({ type: 'queued', item })
  window.setTimeout(flush, (undoable ? UNDO_DELAY_MS : 0) + 50)
  return item.id
}

/** Annule une action pas encore envoyée. */
export function cancel(id) {
  const before = items.length
  items = items.filter((i) => i.id !== id)
  persist()
  emit({ type: 'cancelled', id })
  return items.length < before
}

export async function flush() {
  if (flushing) return
  flushing = true
  try {
    while (items.length > 0) {
      const item = items[0]
      if (item.notBefore > Date.now()) break // délai d'annulation en cours : on garde l'ordre
      try {
        const res = await api.post(item.url, item.body)
        items.shift()
        persist()
        emit({ type: 'sent', item, tour: res.data })
      } catch (err) {
        if (!err.response) {
          emit({ type: 'offline' }) // pas de réseau : nouvel essai plus tard
          break
        }
        // Refus métier (tournée terminée, arrêt introuvable…) : on retire l'action pour ne pas bloquer la file
        items.shift()
        persist()
        emit({ type: 'rejected', item, message: err.response.data?.message || 'Action refusée' })
      }
    }
  } finally {
    flushing = false
  }
}

export function subscribe(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

if (typeof window !== 'undefined') {
  window.addEventListener('online', () => flush())
  window.setInterval(() => flush(), RETRY_MS)
}
