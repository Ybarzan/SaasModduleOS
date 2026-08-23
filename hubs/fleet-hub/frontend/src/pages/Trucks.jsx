import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../services/api'
import StatusBadge from '../components/StatusBadge'

const STATUS_OPTIONS = [
  { value: '', label: 'Tous les statuts' },
  { value: 'ROULAGE', label: 'En roulage' },
  { value: 'ARRET', label: "À l'arrêt" },
  { value: 'REPOS', label: 'Repos' },
  { value: 'ALERTE', label: 'Alerte' },
  { value: 'IMMOBILISE', label: 'Immobilisé' }
]

export default function Trucks() {
  const [trucks, setTrucks] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  useEffect(() => {
    setLoading(true)
    api
      .get('/trucks')
      .then((res) => setTrucks(res.data))
      .catch(() => setError('Impossible de charger les camions'))
      .finally(() => setLoading(false))
  }, [])

  const filtered = useMemo(() => {
    const q = search.toLowerCase()
    return trucks.filter((t) =>
      (statusFilter === '' || t.currentStatus === statusFilter) &&
      `${t.brand} ${t.model} ${t.registration} ${t.driverName || ''} ${t.truckType}`
        .toLowerCase()
        .includes(q)
    )
  }, [trucks, search, statusFilter])

  const counts = useMemo(() => {
    const c = { total: trucks.length }
    for (const opt of STATUS_OPTIONS) {
      if (opt.value) c[opt.value] = trucks.filter((t) => t.currentStatus === opt.value).length
    }
    return c
  }, [trucks])

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Camions</h2>
          <p>État de la flotte et affectation des chauffeurs</p>
        </div>
        <div className="header-actions">
          <input
            type="search"
            placeholder="Rechercher (véhicule, chauffeur)…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="search-input"
          />
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            {STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}{o.value ? ` (${counts[o.value] ?? 0})` : ''}</option>
            ))}
          </select>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {loading && <div className="loading-spinner" aria-label="Chargement des données…">Chargement…</div>}

      <div className="truck-cards">
        {filtered.map((t) => (
          <Link key={t.id} to={`/trucks/${t.id}`} className="truck-card">
            <div className="truck-card-top">
              <div>
                <strong>{t.brand} {t.model}</strong>
                <span className="muted block">{t.registration} · {t.modelYear}</span>
              </div>
              <StatusBadge status={t.currentStatus} />
            </div>
            <div className="truck-card-grid">
              <div className="couple-stat">
                <span>Type</span>
                <b>{t.truckType.toLowerCase()}</b>
              </div>
              <div className="couple-stat">
                <span>Énergie</span>
                <b>{t.fuelType === 'DIESEL' ? 'Diesel' : 'Électrique'}</b>
              </div>
              <div className="couple-stat">
                <span>Conso réf.</span>
                <b>{t.expectedConsumptionL100Km} L/100</b>
              </div>
              <div className="couple-stat">
                <span>Chauffeur</span>
                <b>{t.driverName || '—'}</b>
              </div>
            </div>
            <div className="couple-card-footer">
              <span className="muted">
                🛰️ {t.lastGpsUpdate ? new Date(t.lastGpsUpdate).toLocaleString('fr-FR') : 'Pas de GPS'}
              </span>
              <span className="link">Détails →</span>
            </div>
          </Link>
        ))}
        {filtered.length === 0 && !error && (
          <p className="muted table-empty">Aucun camion ne correspond aux filtres</p>
        )}
      </div>

      <div className="card desktop-table">
        <div className="card-title">
          <h3>État de la flotte</h3>
          <span className="muted">{filtered.length} véhicule(s)</span>
        </div>
        <div className="table-scroll">
          <table className="table table-hover">
            <thead>
              <tr>
                <th>Véhicule</th>
                <th>Type</th>
                <th>Énergie</th>
                <th>Statut</th>
                <th>Chauffeur</th>
                <th>Conso réf.</th>
                <th>Dernier GPS</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((t) => (
                <tr key={t.id}>
                  <td className="cell-strong">
                    <Link to={`/trucks/${t.id}`} className="cell-link">
                      <strong>{t.brand} {t.model}</strong>
                      <span className="muted block">{t.registration} · {t.modelYear}</span>
                    </Link>
                  </td>
                  <td>{t.truckType.toLowerCase()}</td>
                  <td>{t.fuelType === 'DIESEL' ? 'Diesel' : 'Électrique'}</td>
                  <td><StatusBadge status={t.currentStatus} /></td>
                  <td>
                    {t.driverName && t.assignmentId ? (
                      <Link to={`/drivers/${t.assignmentId}`} className="link">{t.driverName}</Link>
                    ) : (
                      <span className="muted">Non affecté</span>
                    )}
                  </td>
                  <td>{t.expectedConsumptionL100Km} L/100</td>
                  <td className="muted">{t.lastGpsUpdate ? new Date(t.lastGpsUpdate).toLocaleString('fr-FR') : '—'}</td>
                </tr>
              ))}
              {filtered.length === 0 && !error && (
                <tr><td colSpan="7" className="table-empty">Aucun camion ne correspond aux filtres</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
