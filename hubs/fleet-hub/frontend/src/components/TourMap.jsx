import L from 'leaflet'
import { MapContainer, Marker, Polyline, Popup, TileLayer } from 'react-leaflet'
import { hhmm } from '../tours'
import { TILE_ATTRIBUTION, TILE_URL } from '../mapTiles'

const STOP_COLORS = { A_FAIRE: '#6d9dc2', FAIT: '#7fb98f', ECHEC: '#c98b8f' }

const numberIcon = (n, status, late) =>
  L.divIcon({
    className: '',
    html: `<span class="stop-pin${late ? ' late' : ''}" style="background:${STOP_COLORS[status] || '#6d9dc2'}">${n}</span>`,
    iconSize: [26, 26],
    iconAnchor: [13, 13]
  })

const depotIcon = L.divIcon({
  className: '',
  html: '<span class="stop-pin depot">D</span>',
  iconSize: [26, 26],
  iconAnchor: [13, 13]
})

/** Carte d'une tournée : dépôt, arrêts numérotés dans l'ordre de passage, tracé. */
export default function TourMap({ tour, height = 360 }) {
  const located = tour.stops.filter((s) => s.latitude != null && s.longitude != null)
  const depot = tour.depotLatitude != null ? [tour.depotLatitude, tour.depotLongitude] : null

  const pts = located.map((s) => [s.latitude, s.longitude])
  const path = depot ? [depot, ...pts, depot] : pts

  const bounds = path.length > 0 ? L.latLngBounds(path).pad(0.15) : null

  if (!bounds) {
    return (
      <p className="muted table-empty">
        Aucun arrêt localisé : renseignez les coordonnées des sites.
      </p>
    )
  }

  return (
    <div className="tour-map" style={{ height }}>
      <MapContainer
        bounds={bounds}
        style={{ height: '100%', width: '100%' }}
        key={path.map((p) => p.join()).join('|')}
      >
        <TileLayer url={TILE_URL} attribution={TILE_ATTRIBUTION} />
        {path.length > 1 && (
          <Polyline positions={path} pathOptions={{ color: '#7db2d4', weight: 3, opacity: 0.8 }} />
        )}
        {depot && (
          <Marker position={depot} icon={depotIcon}>
            <Popup>
              <strong>{tour.depotName}</strong>
              <div>Départ {hhmm(tour.plannedStart)}</div>
            </Popup>
          </Marker>
        )}
        {located.map((s) => (
          <Marker
            key={s.id}
            position={[s.latitude, s.longitude]}
            icon={numberIcon(s.sequence, s.status, s.plannedLatenessMinutes > 0)}
          >
            <Popup>
              <strong>
                {s.sequence}. {s.siteName}
              </strong>
              <div>{[s.address, s.city].filter(Boolean).join(', ')}</div>
              <div>
                Arrivée prévue {hhmm(s.plannedArrival)}
                {s.windowStart || s.windowEnd
                  ? ` · créneau ${hhmm(s.windowStart)}–${hhmm(s.windowEnd)}`
                  : ''}
              </div>
            </Popup>
          </Marker>
        ))}
      </MapContainer>
    </div>
  )
}
