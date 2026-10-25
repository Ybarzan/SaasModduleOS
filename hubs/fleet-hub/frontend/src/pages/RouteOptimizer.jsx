import { useState } from 'react'
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from 'react-leaflet'
import L from 'leaflet'
import api from '../services/api'

const CITIES = [
  'Paris',
  'Lyon',
  'Marseille',
  'Toulouse',
  'Bordeaux',
  'Lille',
  'Nantes',
  'Strasbourg',
  'Bruxelles',
  'Amsterdam',
  'Francfort',
  'Madrid',
  'Rome',
  'Londres',
  'Hamburg',
  'Barcelone',
  'Casablanca',
  'Alger',
  'Tunis',
  'Warszawa',
  'Prague',
  'Vienne',
  'Zurich',
  'Rotterdam',
  'Anvers',
  'Gdansk',
  'Le Havre',
  'Belfort'
]

const COORDS = {
  Paris: [48.8566, 2.3522],
  Lyon: [47.764, 4.8357],
  Marseille: [43.2965, 5.3698],
  Toulouse: [43.6047, 1.4442],
  Bordeaux: [44.8378, -0.5792],
  Lille: [50.6292, 3.0573],
  Nantes: [47.2184, -1.5536],
  Strasbourg: [48.5734, 7.7521],
  Bruxelles: [50.8503, 4.3517],
  Amsterdam: [52.3676, 4.9041],
  Francfort: [50.1109, 8.6821],
  Madrid: [40.4168, -3.7038],
  Rome: [41.9028, 12.4964],
  Londres: [51.5074, -0.1278],
  Hamburg: [53.5511, 9.9937],
  Barcelone: [41.3874, 2.1686],
  Casablanca: [33.5731, -7.5898],
  Alger: [36.7538, 3.0588],
  Tunis: [36.8065, 10.1815],
  Warszawa: [52.2297, 21.0122],
  Prague: [50.0755, 14.4378],
  Vienne: [48.2082, 16.3738],
  Zurich: [47.3769, 8.5417],
  Rotterdam: [51.9244, 4.4777],
  Anvers: [51.2194, 4.4025],
  Gdansk: [54.352, 18.6466],
  'Le Havre': [49.4944, 0.1079],
  Belfort: [47.638, 6.863]
}

const createIcon = (color) =>
  L.divIcon({
    className: 'custom-marker',
    html: `<div style="width:22px;height:22px;border-radius:50%;background:${color};border:3px solid white;box-shadow:0 2px 6px rgba(0,0,0,0.3);"></div>`,
    iconSize: [22, 22],
    iconAnchor: [11, 11]
  })

const ORIGIN_ICON = createIcon('#7fb98f')
const STOP_ICON = createIcon('#0d9488')
const DEST_ICON = createIcon('#c98b8f')

function FitBounds({ positions }) {
  const map = useMap()
  if (positions.length > 0) map.fitBounds(positions, { padding: [40, 40] })
  return null
}

export default function RouteOptimizer() {
  const [origin, setOrigin] = useState('Paris')
  const [destination, setDestination] = useState('Lyon')
  const [stops, setStops] = useState(['Bordeaux'])
  const [newStop, setNewStop] = useState('')
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const addStop = () => {
    if (!newStop) return
    if (stops.includes(newStop)) {
      setError('Stop déjà ajouté')
      return
    }
    setError('')
    setStops([...stops, newStop])
    setNewStop('')
  }

  const removeStop = (idx) => setStops(stops.filter((_, i) => i !== idx))

  const handleOptimize = async () => {
    setLoading(true)
    setError('')
    try {
      const res = await api.post('/logistics/route-optimization', {
        originCountry: origin,
        destinationCountry: destination,
        stops: stops.map((s) => ({ city: s }))
      })
      setResult(res.data)
    } catch {
      setError("Erreur lors de l'optimisation")
    } finally {
      setLoading(false)
    }
  }

  const allCityNames = [origin, ...stops, destination]
  const polylinePositions = allCityNames.map((c) => COORDS[c]).filter(Boolean)
  const mapMarkers = allCityNames
    .filter((c) => COORDS[c])
    .map((city, idx) => ({
      city,
      pos: COORDS[city],
      isFirst: idx === 0,
      isLast: idx === allCityNames.length - 1
    }))

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>🛣️ Optimisation d'itinéraire</h2>
          <p>Planifiez un itinéraire multi-stops avec estimation de carburant et péages</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="dashboard-grid">
        <div className="card">
          <div className="card-title">
            <h3>Itinéraire</h3>
          </div>
          <div className="form-row">
            <div className="form-field">
              <label>Départ</label>
              <select value={origin} onChange={(e) => setOrigin(e.target.value)}>
                {CITIES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-field">
              <label>Arrivée</label>
              <select value={destination} onChange={(e) => setDestination(e.target.value)}>
                {CITIES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="form-field" style={{ marginTop: 14 }}>
            <label>Stops intermédiaires</label>
            {stops.length > 0 && (
              <div style={{ marginBottom: 10 }}>
                {stops.map((stop, idx) => (
                  <div key={idx} className="record-card-row">
                    <span>📍 {stop}</span>
                    <span className="muted">
                      Stop {idx + 1}{' '}
                      <button
                        onClick={() => removeStop(idx)}
                        className="link"
                        style={{
                          background: 'none',
                          border: 'none',
                          cursor: 'pointer',
                          color: 'var(--red)'
                        }}
                      >
                        ✕
                      </button>
                    </span>
                  </div>
                ))}
              </div>
            )}
            <div className="form-row" style={{ gridTemplateColumns: '1fr auto' }}>
              <select value={newStop} onChange={(e) => setNewStop(e.target.value)}>
                <option value="">Ajouter un stop…</option>
                {CITIES.filter((c) => c !== origin && c !== destination && !stops.includes(c)).map(
                  (c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  )
                )}
              </select>
              <button className="btn btn-primary" onClick={addStop}>
                + Ajouter
              </button>
            </div>
          </div>

          <div className="form-actions">
            <button
              className="btn btn-primary btn-block"
              disabled={loading}
              onClick={handleOptimize}
            >
              {loading ? 'Calcul…' : "Optimiser l'itinéraire"}
            </button>
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {result ? (
            <>
              <div className="card">
                <div className="card-title">
                  <h3>Résumé</h3>
                </div>
                <div className="truck-card-grid">
                  <div className="couple-stat">
                    <span>Distance</span>
                    <b>{result.totalDistanceKm.toLocaleString('fr-FR')} km</b>
                  </div>
                  <div className="couple-stat">
                    <span>Temps estimé</span>
                    <b>{result.estimatedHours} h</b>
                  </div>
                  <div className="couple-stat">
                    <span>Carburant</span>
                    <b>{result.estimatedFuelLiters} L</b>
                  </div>
                  <div className="couple-stat">
                    <span>Coût total</span>
                    <b>
                      {(result.estimatedFuelCost + result.estimatedTollCost).toLocaleString(
                        'fr-FR'
                      )}{' '}
                      €
                    </b>
                  </div>
                </div>
              </div>

              <div className="card">
                <div className="card-title">
                  <h3>Détail des coûts</h3>
                </div>
                <div className="record-card-row">
                  <span>⛽ Carburant</span>
                  <b>{result.estimatedFuelCost.toLocaleString('fr-FR')} €</b>
                </div>
                <div className="record-card-row">
                  <span>💰 Péages</span>
                  <b>{result.estimatedTollCost.toLocaleString('fr-FR')} €</b>
                </div>
                <div className="record-card-row">
                  <span>⏱ Temps</span>
                  <b>{result.estimatedHours} h de conduite</b>
                </div>
              </div>

              <div className="card">
                <div className="card-title">
                  <h3>Étapes ({result.orderedStops.length})</h3>
                </div>
                {result.orderedStops.map((stop) => (
                  <div key={stop.order} className="record-card-row">
                    <span>
                      <strong>{stop.city}</strong>
                      {stop.country && <span className="muted"> · {stop.country}</span>}
                    </span>
                    <span className="muted">
                      {stop.distanceFromPreviousKm > 0
                        ? `+${stop.distanceFromPreviousKm} km (total ${stop.cumulativeDistanceKm} km)`
                        : 'Départ'}
                    </span>
                  </div>
                ))}
              </div>

              {result.recommendation && <div className="alert">{result.recommendation}</div>}
            </>
          ) : (
            <div className="card" style={{ textAlign: 'center', padding: 40 }}>
              <p className="muted">Ajoutez des stops et cliquez sur « Optimiser »</p>
            </div>
          )}
        </div>
      </div>

      {polylinePositions.length > 0 && (
        <div className="card">
          <div className="card-title">
            <h3>Visualisation de l'itinéraire</h3>
          </div>
          <div className="map-container" style={{ height: 400 }}>
            <MapContainer
              center={[48.8566, 2.3522]}
              zoom={5}
              style={{ height: '100%', width: '100%' }}
              scrollWheelZoom={false}
            >
              <TileLayer
                attribution="&copy; OpenStreetMap contributors"
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              />
              <FitBounds positions={polylinePositions} />
              <Polyline
                positions={polylinePositions}
                pathOptions={{ color: '#0d9488', weight: 4, opacity: 0.8 }}
              />
              {mapMarkers.map((m, idx) => (
                <Marker
                  key={m.city}
                  position={m.pos}
                  icon={m.isFirst ? ORIGIN_ICON : m.isLast ? DEST_ICON : STOP_ICON}
                >
                  <Popup>
                    {m.city}
                    {m.isFirst ? ' (Départ)' : m.isLast ? ' (Arrivée)' : ` (Stop ${idx})`}
                  </Popup>
                </Marker>
              ))}
            </MapContainer>
          </div>
          <div className="map-legend">
            <span className="legend-item">
              <span className="legend-dot" style={{ background: '#7fb98f' }} /> Départ
            </span>
            <span className="legend-item">
              <span className="legend-dot" style={{ background: '#0d9488' }} /> Stop
            </span>
            <span className="legend-item">
              <span className="legend-dot" style={{ background: '#c98b8f' }} /> Arrivée
            </span>
          </div>
        </div>
      )}
    </div>
  )
}
