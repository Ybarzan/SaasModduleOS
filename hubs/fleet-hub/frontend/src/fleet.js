/**
 * Vocabulaire et modules selon le métier de la flotte (profil de la société).
 * Le backend renvoie `fleetProfile` dans la réponse d'authentification.
 */

export const FLEET_PROFILES = [
  {
    value: 'POIDS_LOURD',
    label: 'Transport poids lourds',
    hint: 'Longue distance, tachygraphe, coût au km'
  },
  {
    value: 'MESSAGERIE',
    label: 'Messagerie / distribution',
    hint: 'Tournées multi-arrêts, preuves de livraison'
  },
  {
    value: 'COLLECTE_SANTE',
    label: 'Collecte santé',
    hint: 'Pharmacies ↔ laboratoires, traçabilité, chaîne du froid'
  },
  {
    value: 'MIXTE',
    label: 'Flotte mixte',
    hint: 'Tous les modules'
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

/** Profil effectif (défaut poids lourds pour les comptes antérieurs au champ). */
export const profileOf = (user) => user?.fleetProfile || 'POIDS_LOURD'

/** Les tournées concernent la messagerie, la collecte santé et les flottes mixtes. */
export const usesTours = (user) => profileOf(user) !== 'POIDS_LOURD'

/** Le tachygraphe est central pour les poids lourds ; secondaire ailleurs. */
export const usesTachograph = (user) => ['POIDS_LOURD', 'MIXTE'].includes(profileOf(user))

/** Libellés dépendant du métier. */
export function fleetTerms(user) {
  const heavy = profileOf(user) === 'POIDS_LOURD'
  return {
    vehicles: heavy ? 'Camions' : 'Véhicules',
    vehicle: heavy ? 'camion' : 'véhicule',
    brandIcon: heavy ? '🚛' : profileOf(user) === 'COLLECTE_SANTE' ? '🧪' : '🚐'
  }
}
