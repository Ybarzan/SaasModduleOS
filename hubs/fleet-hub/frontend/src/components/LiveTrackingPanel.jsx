import { useEffect, useState } from 'react'
import api from '../services/api'

const MODE_ICONS = { MARITIME: '🚢', AIR: '✈️', ROAD: '🚛' }
const MODE_LABELS = { MARITIME: 'Maritime', AIR: 'Aérien', ROAD: 'Routier' }

export default function LiveTrackingPanel({ shipmentId, mode = 'ROAD' }) {
  const [updates, setUpdates] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [position, setPosition] = useState(null)
  const [showPosition, setShowPosition] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [notice, setNotice] = useState('')

  const loadTracking = () => {
    setLoading(true)
    setError(false)
    api
      .get(`/tracking/shipments/${shipmentId}`)
      .then((res) => setUpdates(res.data || []))
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }

  const loadPosition = () => {
    api
      .get(`/tracking/shipments/${shipmentId}/position`)
      .then((res) => setPosition(res.data))
      .catch(() => setPosition(null))
  }

  useEffect(() => {
    loadTracking()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [shipmentId])

  const togglePosition = () => {
    const next = !showPosition
    setShowPosition(next)
    if (next) loadPosition()
  }

  const sync = async () => {
    setSyncing(true)
    setNotice('')
    try {
      await api.post(`/tracking/shipments/${shipmentId}/sync`)
      setNotice('Suivi synchronisé')
      loadTracking()
      if (showPosition) loadPosition()
    } catch {
      setNotice('Erreur lors de la synchronisation')
    } finally {
      setSyncing(false)
    }
  }

  return (
    <div className="card">
      <div className="card-title">
        <h3>
          {MODE_ICONS[mode] || '🚛'} Suivi live — {MODE_LABELS[mode] || mode}
        </h3>
        <div className="row-actions">
          <button className="btn btn-outline btn-sm" onClick={togglePosition}>
            🧭 Position
          </button>
          <button className="btn btn-outline btn-sm" onClick={loadTracking}>
            🔄 Rafraîchir
          </button>
        </div>
      </div>

      {notice && <p className="muted">{notice}</p>}

      {loading ? (
        <div className="loading-spinner" aria-label="Chargement du suivi…">
          Chargement…
        </div>
      ) : error ? (
        <div className="alert alert-error">
          Erreur lors du chargement du suivi.{' '}
          <button
            className="link"
            style={{ background: 'none', border: 'none', cursor: 'pointer' }}
            onClick={loadTracking}
          >
            Réessayer
          </button>
        </div>
      ) : updates.length === 0 ? (
        <p className="muted table-empty">
          Aucune donnée de suivi disponible. Configurez une clé API de suivi pour des données en
          temps réel.
        </p>
      ) : (
        <>
          {position && (
            <div className="truck-card-grid">
              <div className="couple-stat">
                <span>Latitude</span>
                <b>{position.latitude?.toFixed(4) ?? '—'}</b>
              </div>
              <div className="couple-stat">
                <span>Longitude</span>
                <b>{position.longitude?.toFixed(4) ?? '—'}</b>
              </div>
              {position.speed != null && (
                <div className="couple-stat">
                  <span>Vitesse</span>
                  <b>
                    {position.speed} {mode === 'MARITIME' ? 'nds' : 'km/h'}
                  </b>
                </div>
              )}
              {position.heading && position.heading !== 'N/A' && (
                <div className="couple-stat">
                  <span>Cap</span>
                  <b>{position.heading}</b>
                </div>
              )}
            </div>
          )}

          <div className="timeline">
            {updates.slice(0, 5).map((update, idx) => (
              <div key={idx} className="timeline-item">
                <span className={`timeline-dot ${idx === 0 ? 'done' : ''}`} />
                <div className="timeline-body">
                  <span className="badge badge-blue">{update.status}</span>
                  <span className="timeline-time">
                    {update.eventTime ? new Date(update.eventTime).toLocaleString('fr-FR') : '—'}
                  </span>
                  {update.location && <div className="muted">📍 {update.location}</div>}
                  {update.description && <p className="muted">{update.description}</p>}
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      <div className="form-actions">
        <button className="btn btn-primary btn-block" disabled={syncing} onClick={sync}>
          {syncing ? 'Synchronisation…' : '🔄 Synchroniser le suivi'}
        </button>
      </div>
    </div>
  )
}
