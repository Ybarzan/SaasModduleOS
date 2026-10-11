import { useEffect, useState } from 'react'
import { MapContainer, TileLayer, Marker, Popup, useMapEvents } from 'react-leaflet'
import L from 'leaflet'
import api from '../services/api'

const DEFAULT_CENTER = [46, 2]
const DEFAULT_ZOOM = 5

function planeIcon(heading, onGround) {
  const color = onGround ? '#9CA3AF' : '#8B5CF6'
  return L.divIcon({
    className: 'plane-marker',
    html: `<div style="transform:rotate(${heading}deg);width:22px;height:22px;display:flex;align-items:center;justify-content:center;">
      <svg viewBox="0 0 24 24" width="22" height="22" fill="${color}" stroke="white" stroke-width="1">
        <path d="M21 16v-2l-8-5V3.5c0-.83-.67-1.5-1.5-1.5S10 2.67 10 3.5V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5l8 2.5z"/>
      </svg>
    </div>`,
    iconSize: [22, 22],
    iconAnchor: [11, 11]
  })
}

function BboxWatcher({ onChange }) {
  const map = useMapEvents({
    moveend() {
      const b = map.getBounds()
      onChange(`${b.getSouth()},${b.getNorth()},${b.getWest()},${b.getEast()}`)
    }
  })
  useEffect(() => {
    const b = map.getBounds()
    onChange(`${b.getSouth()},${b.getNorth()},${b.getWest()},${b.getEast()}`)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return null
}

function parseFlights(states) {
  if (!states) return []
  return states
    .filter((s) => s[5] != null && s[6] != null && !s[8])
    .map((s) => ({
      icao24: s[0] || '',
      callsign: (s[1] || '').trim(),
      originCountry: s[2] || '',
      longitude: s[5] ?? 0,
      latitude: s[6] ?? 0,
      altitude: s[7] || s[13] || 0,
      velocity: s[9] || 0,
      heading: s[10] || 0,
      onGround: s[8] || false
    }))
}

export default function FlightRadar() {
  const [bbox, setBbox] = useState('35,55,-5,15')
  const [flightData, setFlightData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState(null)

  const load = () => {
    setLoading(true)
    api
      .get('/tracking-map/flights', { params: { bbox } })
      .then((res) => setFlightData(res.data))
      .catch(() => {})
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    const id = setInterval(load, 15000)
    return () => clearInterval(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bbox])

  const flights = parseFlights(flightData?.states)

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>✈️ Flight Radar</h2>
          <p>Vols en temps réel — OpenSky Network</p>
        </div>
        <div className="header-actions">
          <span className="badge badge-blue">{flights.length} vol(s) affiché(s)</span>
          <button className="btn btn-outline" onClick={load}>
            🔄 Actualiser
          </button>
        </div>
      </div>

      <div className="map-container tracker-fullscreen">
        <MapContainer center={DEFAULT_CENTER} zoom={DEFAULT_ZOOM} style={{ height: '100%', width: '100%' }}>
          <TileLayer url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png" attribution="&copy; CARTO &copy; OpenStreetMap" />
          <BboxWatcher onChange={setBbox} />
          {flights.map((f) => (
            <Marker
              key={f.icao24}
              position={[f.latitude, f.longitude]}
              icon={planeIcon(f.heading, f.onGround)}
              eventHandlers={{ click: () => setSelected(f) }}
            >
              <Popup>
                <strong>✈ {f.callsign || f.icao24}</strong>
                <div>ICAO24 : {f.icao24}</div>
                <div>Pays : {f.originCountry}</div>
                <div>Altitude : {f.altitude ? `${Math.round(f.altitude)} m` : 'Sol'}</div>
                <div>Vitesse : {f.velocity ? `${Math.round(f.velocity * 3.6)} km/h` : 'N/A'}</div>
                <div>Cap : {Math.round(f.heading || 0)}°</div>
                <div>Statut : {f.onGround ? 'Au sol' : 'En vol'}</div>
              </Popup>
            </Marker>
          ))}
        </MapContainer>
      </div>

      <div className="map-legend">
        <span className="legend-item">
          <span className="legend-dot" style={{ background: '#8B5CF6' }} /> En vol
        </span>
        <span className="legend-item">
          <span className="legend-dot" style={{ background: '#9CA3AF' }} /> Au sol
        </span>
      </div>

      {selected && (
        <div className="card">
          <div className="card-title">
            <h3>✈ {selected.callsign || selected.icao24}</h3>
            <button className="btn btn-outline btn-sm" onClick={() => setSelected(null)}>
              Fermer
            </button>
          </div>
          <div className="truck-card-grid">
            <div className="couple-stat">
              <span>ICAO24</span>
              <b>{selected.icao24}</b>
            </div>
            <div className="couple-stat">
              <span>Pays d'origine</span>
              <b>{selected.originCountry}</b>
            </div>
            <div className="couple-stat">
              <span>Altitude</span>
              <b>{selected.altitude ? `${Math.round(selected.altitude)} m` : 'Sol'}</b>
            </div>
            <div className="couple-stat">
              <span>Vitesse</span>
              <b>{selected.velocity ? `${Math.round(selected.velocity * 3.6)} km/h` : 'N/A'}</b>
            </div>
            <div className="couple-stat">
              <span>Cap</span>
              <b>{Math.round(selected.heading || 0)}°</b>
            </div>
            <div className="couple-stat">
              <span>Statut</span>
              <b>{selected.onGround ? 'Au sol' : 'En vol'}</b>
            </div>
          </div>
        </div>
      )}

      {loading && flights.length === 0 && (
        <div className="loading-spinner" aria-label="Chargement des vols…">
          Chargement…
        </div>
      )}
    </div>
  )
}
