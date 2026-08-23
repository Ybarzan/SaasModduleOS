import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { MapContainer, TileLayer, CircleMarker, Popup } from 'react-leaflet'
import api from '../services/api'
import StatusBadge from '../components/StatusBadge'

const statusColor = {
  ROULAGE: { color: 'var(--green)', label: 'En roulage' },
  ARRET: { color: 'var(--orange)', label: 'À l\'arrêt' },
  REPOS: { color: 'var(--primary-2)', label: 'Repos' },
  ALERTE: { color: 'var(--red)', label: 'Alerte' },
  IMMOBILISE: { color: 'var(--purple)', label: 'Immobilisé' }
}

export default function MapPage() {
  const navigate = useNavigate()
  const [vehicles, setVehicles] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const load = () => {
      setLoading(vehicles.length === 0)
      api
        .get('/map/vehicles')
        .then((res) => setVehicles(res.data))
        .catch(() => setError('Impossible de charger la carte'))
        .finally(() => setLoading(false))
    }
    load()
    const id = setInterval(load, 15000)
    return () => clearInterval(id)
  }, [])

  const center = vehicles.length > 0
    ? [vehicles[0].latitude, vehicles[0].longitude]
    : [46.6, 2.0]

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Carte temps réel</h2>
          <p>Position et statut des véhicules (mise à jour auto toutes les 15 s)</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {loading && vehicles.length === 0 && <div className="loading-spinner" aria-label="Chargement de la carte…">Chargement…</div>}

      <div className="map-legend">
        {Object.entries(statusColor).map(([key, v]) => (
          <span key={key} className="legend-item">
            <span className="legend-dot" style={{ background: v.color }} /> {v.label}
          </span>
        ))}
      </div>

      <div className="map-container">
        <MapContainer center={center} zoom={6} style={{ height: '100%', width: '100%' }}>
          <TileLayer
            url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
            attribution='&copy; OpenStreetMap contributors &copy; CARTO'
          />
          {vehicles.map((v) => {
            const meta = statusColor[v.status] || { color: 'var(--muted)', label: v.status }
            return (
              <CircleMarker
                key={v.truckId}
                center={[v.latitude, v.longitude]}
                radius={v.status === 'ALERTE' || v.status === 'IMMOBILISE' ? 14 : 10}
                pathOptions={{ color: meta.color, fillColor: meta.color, fillOpacity: 0.6, weight: 2 }}
              >
                <Popup>
                  <strong>{v.registration}</strong>
                  <div>{v.brand} {v.model}</div>
                  <div>Statut : <b style={{ color: meta.color }}>{meta.label}</b></div>
                  <div>Vitesse : {v.speedKph} km/h</div>
                  {v.driverName && <div>Chauffeur : {v.driverName}</div>}
                  <div className="muted">{v.lastGpsUpdate ? new Date(v.lastGpsUpdate).toLocaleString('fr-FR') : ''}</div>
                  <div style={{ marginTop: 8 }}>
                    <Link to={`/trucks/${v.truckId}`} className="link">Fiche camion →</Link>
                    {v.assignmentId && (
                      <>
                        {' '}· <Link to={`/drivers/${v.assignmentId}`} className="link">Chauffeur →</Link>
                      </>
                    )}
                  </div>
                </Popup>
              </CircleMarker>
            )
          })}
        </MapContainer>
      </div>

      <div className="map-vehicles-cards">
        {vehicles.map((v) => {
          const meta = statusColor[v.status] || { color: 'var(--muted)', label: v.status }
          return (
            <Link key={v.truckId} to={`/trucks/${v.truckId}`} className="vehicle-card">
              <div className="truck-card-top">
                <div>
                  <strong>{v.registration}</strong>
                  <span className="muted block">{v.brand} {v.model}</span>
                </div>
                <StatusBadge status={v.status} />
              </div>
              <div className="truck-card-grid">
                <div className="couple-stat">
                  <span>Chauffeur</span>
                  <b>{v.driverName || '—'}</b>
                </div>
                <div className="couple-stat">
                  <span>Vitesse</span>
                  <b>{v.speedKph} km/h</b>
                </div>
                <div className="couple-stat">
                  <span>Position</span>
                  <b>{v.latitude.toFixed(3)}, {v.longitude.toFixed(3)}</b>
                </div>
                <div className="couple-stat">
                  <span>Mis à jour</span>
                  <b>{v.lastGpsUpdate ? new Date(v.lastGpsUpdate).toLocaleTimeString('fr-FR') : '—'}</b>
                </div>
              </div>
              <div className="couple-card-footer">
                <span className="link">Détails →</span>
              </div>
            </Link>
          )
        })}
      </div>

      <div className="card desktop-table">
        <div className="card-title">
          <h3>Positions des véhicules</h3>
          <span className="muted">Cliquez sur une ligne pour ouvrir la fiche camion</span>
        </div>
        <div className="table-scroll">
          <table className="table table-hover">
            <thead>
              <tr>
                <th>Véhicule</th>
                <th>Chauffeur</th>
                <th>Statut</th>
                <th>Vitesse</th>
                <th>Position</th>
              </tr>
            </thead>
            <tbody>
              {vehicles.map((v) => (
                <tr key={v.truckId} className="row-clickable" onClick={() => navigate(`/trucks/${v.truckId}`)}>
                  <td className="cell-strong">
                    <Link to={`/trucks/${v.truckId}`} className="cell-link" onClick={(e) => e.stopPropagation()}>
                      {v.registration} <span className="muted">({v.brand} {v.model})</span>
                    </Link>
                  </td>
                  <td>
                    {v.driverName && v.assignmentId ? (
                      <Link to={`/drivers/${v.assignmentId}`} className="link" onClick={(e) => e.stopPropagation()}>{v.driverName}</Link>
                    ) : (
                      <span className="muted">—</span>
                    )}
                  </td>
                  <td><StatusBadge status={v.status} /></td>
                  <td>{v.speedKph} km/h</td>
                  <td className="muted">{v.latitude.toFixed(4)}, {v.longitude.toFixed(4)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
