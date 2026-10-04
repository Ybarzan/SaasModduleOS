/** Libellés et outils partagés du module Tournées. */

export const SITE_KINDS = [
  { value: 'DEPOT', label: 'Dépôt' },
  { value: 'CLIENT', label: 'Client' },
  { value: 'PHARMACIE', label: 'Pharmacie' },
  { value: 'LABORATOIRE', label: 'Laboratoire' },
  { value: 'ETABLISSEMENT_SANTE', label: 'Établissement de santé' },
  { value: 'AUTRE', label: 'Autre' }
]

export const STOP_TYPES = [
  { value: 'LIVRAISON', label: 'Livraison' },
  { value: 'ENLEVEMENT', label: 'Enlèvement' },
  { value: 'COLLECTE', label: 'Collecte' }
]

export const TOUR_STATUS = {
  PLANIFIEE: { label: 'Planifiée', badge: 'badge-gray' },
  EN_COURS: { label: 'En cours', badge: 'badge-blue' },
  TERMINEE: { label: 'Terminée', badge: 'badge-green' },
  ANNULEE: { label: 'Annulée', badge: 'badge-red' }
}

export const STOP_STATUS = {
  A_FAIRE: { label: 'À faire', badge: 'badge-gray' },
  FAIT: { label: 'Fait', badge: 'badge-green' },
  ECHEC: { label: 'Échec', badge: 'badge-red' }
}

/** Motifs d'échec proposés au chauffeur (saisie libre possible). */
export const FAILURE_REASONS = [
  'Site fermé',
  'Destinataire absent',
  'Refus du destinataire',
  'Adresse introuvable',
  'Accès impossible',
  'Colis / échantillon non prêt',
  'Hors délai'
]

const labelOf = (list, v) => list.find((x) => x.value === v)?.label || v || '—'
export const siteKindLabel = (v) => labelOf(SITE_KINDS, v)
export const stopTypeLabel = (v) => labelOf(STOP_TYPES, v)

/** "08:30:00" → "08:30" */
export const hhmm = (t) => (t ? String(t).slice(0, 5) : '—')

export const duration = (minutes) => {
  if (minutes == null) return '—'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h} h ${String(m).padStart(2, '0')}` : `${m} min`
}

export const todayIso = () => {
  const d = new Date()
  const off = d.getTimezoneOffset()
  return new Date(d.getTime() - off * 60000).toISOString().slice(0, 10)
}

export const shiftDate = (iso, days) => {
  const d = new Date(`${iso}T12:00:00`)
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

/** Téléchargement d'un fichier protégé (cookie / jeton) via axios. */
export async function downloadFile(api, url, params, filename) {
  const res = await api.get(url, { params, responseType: 'blob' })
  const href = window.URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = href
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.URL.revokeObjectURL(href)
}
