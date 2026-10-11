import { useEffect, useState } from 'react'
import { MapContainer, TileLayer, Marker, Popup, useMapEvents } from 'react-leaflet'
import L from 'leaflet'
import api from '../services/api'

const DEFAULT_CENTER = [30, 0]
const DEFAULT_ZOOM = 3

function shipIcon(heading, color) {
  return L.divIcon({
    className: 'ship-marker',
    html: `<div style="transform:rotate(${heading}deg);width:22px;height:22px;display:flex;align-items:center;justify-content:center;">
      <svg viewBox="0 0 24 24" width="22" height="22" fill="${color}" stroke="white" stroke-width="1.5">
        <path d="M12 2L4 14h16L12 2z"/>
        <circle cx="12" cy="18" r="3" fill="${color}"/>
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

export default function ShipTracker() {
  const [bbox, setBbox] = useState('35,55,-5,15')
  const [query, setQuery] = useState('')
  const [submitQuery, setSubmitQuery] = useState('')
  const [searchResults, setSearchResults] = useState([])
  const [searchLoading, setSearchLoading] = useState(false)
  const [liveData, setLiveData] = useState(null)
  const [shipments, setShipments] = useState([])

  const loadLive = () => {
    api
      .get('/tracking-map/vessels/live', { params: { bbox } })
      .then((res) => setLiveData(res.data))
      .catch(() => {})
  }

  useEffect(() => {
    loadLive()
    const id = setInterval(loadLive, 15000)
    return () => clearInterval(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bbox])

  useEffect(() => {
    const load = () =>
      api
        .get('/shipments', { params: { page: 0, size: 200 } })
        .then((res) => setShipments(Array.isArray(res.data) ? res.data : res.data.content || []))
        .catch(() => {})
    load()
    const id = setInterval(load, 30000)
    return () => clearInterval(id)
  }, [])

  useEffect(() => {
    if (!submitQuery) {
      setSearchResults([])
      return
    }
    setSearchLoading(true)
    api
      .get('/tracking-map/vessels/search', { params: { query: submitQuery } })
      .then((res) => setSearchResults(res.data || []))
      .catch(() => setSearchResults([]))
      .finally(() => setSearchLoading(false))
  }, [submitQuery])

  const maritimeShipments = shipments.filter((s) => s.status === 'IN_TRANSIT' && s.carrier?.transportModes?.includes('SEA'))

  const liveVessels = (liveData?.vessels || []).filter((v) => v.latitude != null && v.longitude != null)
  const searchShips = searchResults
    .filter((v) => (v.latitude || v.lat) && (v.longitude || v.lon || v.lng))
    .map((v) => ({
      id: v.icao24 || v.mmsi || v.name || Math.random().toString(),
      name: v.name || v.callsign || v.shipname || 'Navire inconnu',
      lat: v.latitude || v.lat,
      lng: v.longitude || v.lon || v.lng,
      heading: v.true_track || v.heading || v.course || 0,
      speed: v.velocity || v.speed || 0
    }))

  const handleSearch = (e) => {
    e.preventDefault()
    if (query.trim()) setSubmitQuery(query.trim())
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>🚢 Ship Tracker</h2>
          <p>Suivi maritime en temps réel</p>
        </div>
        <div className="header-actions">
          <form onSubmit={handleSearch} className="form-row" style={{ gridTemplateColumns: 'auto auto' }}>
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Rechercher un navire (nom, MMSI)…"
              className="search-input"
            />
            <button type="submit" className="btn btn-outline">
              Rechercher
            </button>
          </form>
        </div>
      </div>

      {liveData && !liveData.configured && (
        <div className="alert alert-warning">
          Suivi en direct non configuré — renseignez une clé API AISStream.io pour afficher les navires en temps réel.
        </div>
      )}
      {liveData && liveData.configured && !liveData.connected && (
        <div className="alert alert-error">Clé API AISStream.io configurée mais la connexion au flux échoue.</div>
      )}

      <div className="map-legend">
        <span className="legend-item">
          <span className="legend-dot" style={{ background: '#1B4965' }} /> Résultat de recherche
        </span>
        {liveData?.configured && liveData.connected && (
          <span className="legend-item">
            <span className="legend-dot" style={{ background: '#0F9D58' }} /> Navire en direct ({liveVessels.length})
          </span>
        )}
      </div>

      <div className="map-container tracker-fullscreen">
        <MapContainer center={DEFAULT_CENTER} zoom={DEFAULT_ZOOM} style={{ height: '100%', width: '100%' }}>
          <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" attribution="&copy; OpenStreetMap contributors" />
          <BboxWatcher onChange={setBbox} />
          {searchShips.map((s) => (
            <Marker key={s.id} position={[s.lat, s.lng]} icon={shipIcon(s.heading, '#1B4965')}>
              <Popup>
                <strong>{s.name}</strong>
                <div>
                  {s.lat.toFixed(4)}°, {s.lng.toFixed(4)}°
                </div>
                <div>Cap {Math.round(s.heading)}° · {Math.round(s.speed)} kts</div>
              </Popup>
            </Marker>
          ))}
          {liveVessels.map((v) => (
            <Marker key={v.mmsi} position={[v.latitude, v.longitude]} icon={shipIcon(v.heading ?? v.course ?? 0, '#0F9D58')}>
              <Popup>
                <strong>{v.shipName?.trim() || `MMSI ${v.mmsi}`}</strong>
                <div>MMSI : {v.mmsi}</div>
                <div>
                  {v.latitude.toFixed(4)}°, {v.longitude.toFixed(4)}°
                </div>
                <div>Cap {v.course ?? v.heading ?? 0}° · {v.speedKnots ?? 0} kts</div>
                <div className="muted">Source : AISStream.io (direct)</div>
              </Popup>
            </Marker>
          ))}
        </MapContainer>
      </div>

      {searchLoading && <p className="muted">Recherche en cours…</p>}

      <div className="card">
        <div className="card-title">
          <h3>⚓ Expéditions maritimes en transit</h3>
        </div>
        {maritimeShipments.length === 0 ? (
          <p className="muted table-empty">Aucune expédition en transit</p>
        ) : (
          <div className="record-cards" style={{ display: 'block' }}>
            {maritimeShipments.slice(0, 10).map((s) => (
              <div key={s.id} className="record-card-row">
                <span>{s.orderNumber}</span>
                <span className="muted">{s.consigneeCity || s.consigneeCountry}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
