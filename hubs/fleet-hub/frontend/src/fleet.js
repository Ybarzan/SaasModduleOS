/**
 * Vocabulaire et modules selon l'activité de la société (profil de flotte).
 * Le backend renvoie `fleetProfile` dans la réponse d'authentification.
 */

/** Les trois activités proposées au portail d'entrée et à l'inscription. */
export const ACTIVITIES = [
  {
    value: 'POIDS_LOURD',
    slug: 'poids-lourd',
    icon: '🚛',
    label: 'Transport poids lourds',
    hint: 'Longue distance, tachygraphe, coût au km',
    tagline: 'KPIs chauffeur × camion, conformité 561/2006, maintenance'
  },
  {
    value: 'MESSAGERIE',
    slug: 'messagerie',
    icon: '🚐',
    label: 'Transport messagerie',
    hint: 'Tournées multi-arrêts, preuves de livraison',
    tagline: 'Planification, optimisation des tournées, preuves de livraison'
  },
  {
    value: 'COLLECTE_SANTE',
    slug: 'medical',
    icon: '🧪',
    label: 'Transport médical',
    hint: 'Pharmacies, laboratoires, traçabilité, chaîne du froid',
    tagline: 'Prélèvements sanguins, PCR, urines : comptage, journal de garde, non-conformités'
  }
]

/** Tous les profils connus (MIXTE = comptes historiques, plus proposé). */
export const FLEET_PROFILES = [
  ...ACTIVITIES,
  {
    value: 'MIXTE',
    slug: 'mixte',
    icon: '🚚',
    label: 'Flotte mixte',
    hint: 'Poids lourds et messagerie'
  }
]

export const VEHICLE_TYPES = [
  { value: 'TRACTEUR', label: 'Tracteur (PL)' },
  { value: 'PORTEUR', label: 'Porteur (PL)' },
  { value: 'FOURGON', label: 'Fourgon (PL)' },
  { value: 'VUL', label: 'Camionnette / VUL ≤ 3,5 t' },
  { value: 'VL', label: 'Véhicule léger' }
]

export const FUEL_TYPES = [
  { value: 'DIESEL', label: 'Diesel' },
  { value: 'ELECTRIC', label: 'Électrique' },
  { value: 'ESSENCE', label: 'Essence' },
  { value: 'HYBRIDE', label: 'Hybride' },
  { value: 'GNV', label: 'GNV / bioGNV' }
]

const labelOf = (list, value) => list.find((x) => x.value === value)?.label || value || '—'

export const vehicleTypeLabel = (value) => labelOf(VEHICLE_TYPES, value)
export const fuelLabel = (value) => labelOf(FUEL_TYPES, value)
export const fleetProfileLabel = (value) => labelOf(FLEET_PROFILES, value)

/** Activité choisie au portail : « medical » → COLLECTE_SANTE (null si inconnue). */
export const activityFromSlug = (slug) => ACTIVITIES.find((a) => a.slug === slug) || null
export const activityOf = (value) => FLEET_PROFILES.find((a) => a.value === value) || ACTIVITIES[0]

const ACTIVITY_KEY = 'fh_activity'

/** Dernière activité choisie au portail (mémorisée sur cet appareil). */
export function rememberedActivity() {
  try {
    return activityFromSlug(localStorage.getItem(ACTIVITY_KEY))
  } catch {
    return null
  }
}

export function rememberActivity(slug) {
  try {
    localStorage.setItem(ACTIVITY_KEY, slug)
  } catch {
    /* préférence non mémorisée */
  }
}

/** Activité choisie au portail (?activite=medical), sinon la dernière utilisée sur cet appareil. */
export function chosenActivity(search) {
  const slug = new window.URLSearchParams(search || '').get('activite')
  return activityFromSlug(slug) || rememberedActivity()
}

/** Profil effectif (défaut poids lourds pour les comptes antérieurs au champ). */
export const profileOf = (user) => user?.fleetProfile || 'POIDS_LOURD'

/** Espace cloisonné Transport médical. */
export const isMedical = (user) => profileOf(user) === 'COLLECTE_SANTE'

/** Les tournées concernent la messagerie, le transport médical et les flottes mixtes. */
export const usesTours = (user) => profileOf(user) !== 'POIDS_LOURD'

/** Le tachygraphe est central pour les poids lourds ; secondaire ailleurs. */
export const usesTachograph = (user) => ['POIDS_LOURD', 'MIXTE'].includes(profileOf(user))

/** La bourse de fret FleetMarket ne concerne pas le transport médical. */
export const usesMarketplace = (user) => !isMedical(user)

/** Le domaine Transport (expéditions, transporteurs, tarifs, suivi) ne concerne pas le transport médical. */
export const usesFreight = (user) => !isMedical(user)

/** Libellés dépendant du métier. */
export function fleetTerms(user) {
  const heavy = profileOf(user) === 'POIDS_LOURD'
  const medical = isMedical(user)
  return {
    vehicles: heavy ? 'Camions' : 'Véhicules',
    vehicle: heavy ? 'camion' : 'véhicule',
    sites: medical ? 'Points de collecte' : 'Sites',
    brandIcon: heavy ? '🚛' : medical ? '🧪' : '🚐',
    brandSub: medical ? 'TRANSPORT MÉDICAL' : heavy ? 'TRANSPORT POIDS LOURDS' : 'MESSAGERIE'
  }
}
