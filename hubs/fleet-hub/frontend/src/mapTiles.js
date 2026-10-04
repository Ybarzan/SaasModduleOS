/**
 * Fond de carte Leaflet. Par défaut : tuiles OpenStreetMap standard (sans clé,
 * usage modéré — politique https://operations.osmfoundation.org/policies/tiles/).
 * En production à fort trafic, définir VITE_MAP_TILE_URL (et VITE_MAP_ATTRIBUTION)
 * vers un fournisseur avec clé (MapTiler, Stadia, CARTO…) et ajouter son domaine
 * à la CSP img-src (caddy/Caddyfile).
 */
export const TILE_URL =
  import.meta.env.VITE_MAP_TILE_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'

export const TILE_ATTRIBUTION =
  import.meta.env.VITE_MAP_ATTRIBUTION ||
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
