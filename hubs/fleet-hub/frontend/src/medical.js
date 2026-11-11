/** Libellés et outils partagés de l'activité Transport médical. */

/** Catégories de produits comptés à chaque passage (pas de suivi tube par tube). */
export const MEDICAL_PRODUCTS = [
  { value: 'SANG', label: 'Sang', short: 'Sang', hint: 'tubes de prélèvement sanguin', icon: '🩸' },
  {
    value: 'PCR',
    label: 'PCR / écouvillons',
    short: 'PCR',
    hint: 'tests PCR, écouvillons',
    icon: '🧫'
  },
  { value: 'URINE', label: 'Urines', short: 'Urines', hint: 'flacons d’urine', icon: '🧴' },
  {
    value: 'AUTRE',
    label: 'Autres',
    short: 'Autres',
    hint: 'autres produits biologiques',
    icon: '📦'
  }
]

/** Plages de température usuelles du transport d'échantillons. */
export const TEMPERATURE_PRESETS = [
  { label: 'Ambiant 15–25 °C', min: 15, max: 25 },
  { label: 'Réfrigéré 2–8 °C', min: 2, max: 8 },
  { label: 'Congelé ≤ −18 °C', min: -80, max: -18 }
]

export const NC_TYPES = {
  ECART_QUANTITE: 'Écart de quantité',
  ECART_TEMPERATURE: 'Rupture chaîne du froid',
  DELAI_ACHEMINEMENT: "Délai d'acheminement",
  COLLECTE_NON_REALISEE: 'Collecte non réalisée',
  EMBALLAGE: 'Emballage / conditionnement',
  AUTRE: 'Autre'
}

export const NC_SEVERITY = {
  MINEURE: { label: 'Mineure', badge: 'badge-gray' },
  MAJEURE: { label: 'Majeure', badge: 'badge-orange' },
  CRITIQUE: { label: 'Critique', badge: 'badge-red' }
}

export const NC_STATUS = {
  OUVERTE: { label: 'Ouverte', badge: 'badge-red' },
  EN_TRAITEMENT: { label: 'En traitement', badge: 'badge-orange' },
  CLOTUREE: { label: 'Clôturée', badge: 'badge-green' }
}

export const CUSTODY_EVENTS = {
  TOURNEE_DEMARREE: { label: 'Tournée démarrée', icon: '▶' },
  ARRIVEE: { label: 'Arrivée sur site', icon: '📍' },
  PRISE_EN_CHARGE: { label: 'Prise en charge', icon: '⬆' },
  REMISE: { label: 'Remise au laboratoire', icon: '⬇' },
  ECHEC: { label: 'Passage en échec', icon: '✕' },
  CORRECTION: { label: 'Correction', icon: '✎' },
  TOURNEE_TERMINEE: { label: 'Tournée terminée', icon: '■' },
  TOURNEE_ANNULEE: { label: 'Tournée annulée', icon: '⊘' },
  NC_OUVERTE: { label: 'Non-conformité ouverte', icon: '⚠' },
  NC_CLOTUREE: { label: 'Non-conformité clôturée', icon: '✓' }
}

export const productLabel = (v) => MEDICAL_PRODUCTS.find((p) => p.value === v)?.short || v

/** { SANG: 3, PCR: 2 } → « Sang 3 · PCR 2 » */
export function describeProducts(map) {
  if (!map) return '—'
  const parts = MEDICAL_PRODUCTS.filter((p) => map[p.value] > 0).map(
    (p) => `${p.short} ${map[p.value]}`
  )
  return parts.length ? parts.join(' · ') : 'aucun produit'
}

/** « SANG=3,PCR=2 » (journal de garde) → { SANG: 3, PCR: 2 } */
export function parseProducts(text) {
  const map = {}
  if (!text) return map
  text.split(',').forEach((pair) => {
    const [k, v] = pair.split('=')
    if (k && v) map[k] = Number(v)
  })
  return map
}

export const totalOf = (map) => Object.values(map || {}).reduce((s, v) => s + (Number(v) || 0), 0)

/** Ne garde que les quantités strictement positives. */
export const cleanProducts = (map) =>
  Object.fromEntries(Object.entries(map || {}).filter(([, v]) => Number(v) > 0))

export const minutesLabel = (m) => {
  if (m == null) return '—'
  const h = Math.floor(m / 60)
  const min = m % 60
  return h > 0 ? `${h} h ${String(min).padStart(2, '0')}` : `${min} min`
}

export const dateTime = (iso) =>
  iso
    ? new Date(iso).toLocaleString('fr-FR', {
        day: '2-digit',
        month: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      })
    : '—'
