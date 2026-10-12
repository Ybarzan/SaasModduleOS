import { useEffect, useState } from 'react'
import api from '../services/api'
import StatCard from '../components/StatCard'

const HEALTH_LABELS = {
  HEALTHY: 'Sain',
  DEGRADED: 'Dégradé',
  DOWN: 'Hors ligne',
  UNKNOWN: 'Inconnu'
}
const HEALTH_COLORS = { HEALTHY: 'green', DEGRADED: 'orange', DOWN: 'red', UNKNOWN: 'gray' }

export default function ProviderHealth() {
  const [health, setHealth] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const load = () =>
      api
        .get('/providers/health')
        .then((res) => setHealth(res.data || []))
        .catch(() => setError("Impossible de charger l'état de santé"))
        .finally(() => setLoading(false))
    load()
    const id = setInterval(load, 30000)
    return () => clearInterval(id)
  }, [])

  const connected = health.length
  const healthy = health.filter((h) => h.healthStatus === 'HEALTHY').length
  const degraded = health.filter((h) => h.healthStatus === 'DEGRADED').length
  const down = health.filter((h) => h.healthStatus === 'DOWN').length

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>État de santé</h2>
          <p>Vue d'ensemble de vos fournisseurs de tarifs</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {loading && (
        <div className="loading-spinner" aria-label="Chargement de l'état de santé…">
          Chargement…
        </div>
      )}

      <div className="north-star-grid">
        <StatCard label="Connecté(s)" value={connected} icon="🔌" tone="blue" />
        <StatCard label="Sain(s)" value={healthy} icon="✅" tone="green" />
        <StatCard label="Dégradé(s)" value={degraded} icon="⚠️" tone="purple" />
        <StatCard label="Hors ligne" value={down} icon="📴" tone="red" />
      </div>

      <div className="card">
        <div className="card-title">
          <h3>Derniers statuts</h3>
        </div>
        {health.length > 0 ? (
          <div>
            {health.map((h) => (
              <div key={h.providerType} className="record-card-row" style={{ padding: '10px 0' }}>
                <span>
                  <strong style={{ textTransform: 'none', fontSize: 14 }}>{h.providerName}</strong>
                  <span className="muted block">{h.providerType}</span>
                </span>
                <span>
                  <span className={`badge badge-${HEALTH_COLORS[h.healthStatus]}`}>
                    {HEALTH_LABELS[h.healthStatus]}
                  </span>
                  <span className="muted block" style={{ textAlign: 'right', marginTop: 4 }}>
                    {h.lastHealthCheck ? new Date(h.lastHealthCheck).toLocaleString('fr-FR') : '—'}
                  </span>
                  {h.consecutiveFailures > 0 && (
                    <span
                      className="text-danger block"
                      style={{ textAlign: 'right', fontSize: 11 }}
                    >
                      {h.consecutiveFailures} échec(s)
                    </span>
                  )}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <p className="muted table-empty">Aucun fournisseur configuré</p>
        )}
      </div>
    </div>
  )
}
