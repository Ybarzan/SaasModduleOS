import { useState } from 'react'
import api from '../services/api'

const MODES = [
  { value: 'MARITIME', label: 'Maritime', icon: '🚢', placeholder: 'MMSI (ex : 226000000)' },
  { value: 'AIR', label: 'Aérien', icon: '✈️', placeholder: 'N° de vol IATA (ex : AF1234)' },
  { value: 'ROAD', label: 'Routier', icon: '🚛', placeholder: 'N° de suivi transporteur' }
]

export default function TrackingLookup() {
  const [trackingNumber, setTrackingNumber] = useState('')
  const [mode, setMode] = useState('ROAD')
  const [submitted, setSubmitted] = useState(false)
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [queryLabel, setQueryLabel] = useState('')

  const selectedMode = MODES.find((m) => m.value === mode) || MODES[2]

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!trackingNumber.trim()) return
    setSubmitted(true)
    setLoading(true)
    setError('')
    setQueryLabel(selectedMode.label)
    try {
      const res = await api.post('/tracking/lookup', {
        trackingNumber: trackingNumber.trim(),
        mode
      })
      setResults(res.data || [])
    } catch {
      setError('Erreur lors de la recherche')
      setResults([])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Suivi d'expédition</h2>
          <p>Entrez un numéro de tracking pour suivre un envoi en temps réel</p>
        </div>
      </div>

      <div className="card">
        <form onSubmit={handleSubmit}>
          <div className="chip-row" style={{ marginBottom: 16 }}>
            {MODES.map((m) => (
              <button
                key={m.value}
                type="button"
                className={`chip ${mode === m.value ? 'active' : ''}`}
                onClick={() => setMode(m.value)}
              >
                {m.icon} {m.label}
              </button>
            ))}
          </div>
          <div className="form-row">
            <input
              type="text"
              value={trackingNumber}
              onChange={(e) => setTrackingNumber(e.target.value)}
              placeholder={selectedMode.placeholder}
              className="search-input"
              style={{ gridColumn: '1 / -1' }}
            />
          </div>
          <div className="form-actions">
            <button
              type="submit"
              className="btn btn-primary"
              disabled={!trackingNumber.trim() || loading}
            >
              {loading ? 'Recherche…' : 'Rechercher'}
            </button>
          </div>
        </form>
      </div>

      {submitted && (
        <div className="card">
          <div className="card-title">
            <h3>Résultats — {queryLabel}</h3>
          </div>
          {loading ? (
            <div className="loading-spinner" aria-label="Recherche en cours…">
              Chargement…
            </div>
          ) : error ? (
            <div className="alert alert-error">{error}</div>
          ) : results.length === 0 ? (
            <p className="muted table-empty">
              Aucun résultat trouvé. Vérifiez le numéro de tracking et le mode de transport
              sélectionné.
            </p>
          ) : (
            <div className="timeline">
              {results.map((update, idx) => (
                <div key={idx} className="timeline-item">
                  <span className={`timeline-dot ${idx === 0 ? 'done' : ''}`} />
                  <div className="timeline-body">
                    <span className={`badge ${idx === 0 ? 'badge-blue' : 'badge-gray'}`}>
                      {update.status}
                    </span>
                    <span className="timeline-time">
                      {update.eventTime ? new Date(update.eventTime).toLocaleString('fr-FR') : '—'}
                    </span>
                    {update.source && <span className="muted"> · {update.source}</span>}
                    {update.location && <div className="muted">📍 {update.location}</div>}
                    {update.description && <p className="muted">{update.description}</p>}
                    {update.latitude != null && update.longitude != null && (
                      <p className="muted" style={{ fontFamily: 'var(--font-mono)' }}>
                        {update.latitude.toFixed(4)}°, {update.longitude.toFixed(4)}°
                      </p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
