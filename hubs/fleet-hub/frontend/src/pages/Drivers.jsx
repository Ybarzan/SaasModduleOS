import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import api from '../services/api'
import PeriodSelector from '../components/PeriodSelector'

const scoreClass = (s) => (s >= 75 ? 'good' : s >= 50 ? 'mid' : 'bad')

const KPI_META = {
  costPerKm: { label: 'Coût au km', dir: 'asc', col: 'costPerKm' },
  utilization: { label: "Taux d'utilisation", dir: 'desc', col: 'utilizationRate' },
  maintenance: { label: 'Conformité maintenance', dir: 'desc', col: 'maintenanceComplianceRate' },
  downtime: { label: 'Indisponibilité imprévue', dir: 'asc', col: 'unplannedDowntimeRate' }
}

const SORTABLE = [
  { key: 'driverName', label: 'Chauffeur', accessor: (c) => c.driverName, dir: 'asc' },
  { key: 'score', label: 'Score', accessor: (c) => c.performanceScore, dir: 'desc' },
  { key: 'km', label: 'Km', accessor: (c) => c.totalKm, dir: 'desc' },
  { key: 'costPerKm', label: 'Coût/km', accessor: (c) => c.costPerKm, dir: 'asc' },
  { key: 'utilization', label: 'Utilisation', accessor: (c) => c.utilizationRate, dir: 'desc' },
  { key: 'eco', label: 'Éco-conduite', accessor: (c) => c.ecoScore, dir: 'desc' },
  { key: 'compliance', label: 'Conformité', accessor: (c) => c.drivingTimeComplianceRate, dir: 'desc' },
  { key: 'conso', label: 'Conso', accessor: (c) => c.consumptionPer100Km, dir: 'asc' },
  { key: 'events', label: 'Événements', accessor: (c) => c.riskEventsTotal, dir: 'asc' }
]

export default function Drivers() {
  const navigate = useNavigate()
  const [period, setPeriod] = useState('MONTH')
  const [couples, setCouples] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [sortKey, setSortKey] = useState('score')
  const [sortDir, setSortDir] = useState('desc')
  const [params] = useSearchParams()

  useEffect(() => {
    setLoading(true)
    api
      .get('/kpis/couples', { params: { period } })
      .then((res) => setCouples(res.data))
      .catch(() => setError('Impossible de charger les chauffeurs'))
      .finally(() => setLoading(false))
  }, [period])

  const focusKpi = params.get('kpi')
  const focusMeta = focusKpi ? KPI_META[focusKpi] : null

  useEffect(() => {
    if (focusMeta) {
      setSortKey(focusMeta.col)
      setSortDir(focusMeta.dir)
    }
  }, [focusMeta])

  const sorted = useMemo(() => {
    const q = search.toLowerCase()
    const col = SORTABLE.find((s) => s.key === sortKey)
    const list = couples.filter((c) =>
      `${c.driverName} ${c.registration} ${c.brand} ${c.model}`.toLowerCase().includes(q)
    )
    if (!col) return list
    const dir = sortDir === 'asc' ? 1 : -1
    return [...list].sort((a, b) => {
      const va = col.accessor(a)
      const vb = col.accessor(b)
      if (va === vb) return 0
      return va > vb ? dir : -dir
    })
  }, [couples, search, sortKey, sortDir])

  const toggleSort = (key) => {
    const col = SORTABLE.find((s) => s.key === key)
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortKey(key)
      setSortDir(col.dir)
    }
  }

  const sortArrow = (key) => {
    if (sortKey !== key) return ''
    return sortDir === 'asc' ? ' ↑' : ' ↓'
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Chauffeurs</h2>
          <p>KPIs détaillés par couple Chauffeur × Camion</p>
        </div>
        <div className="header-actions">
          <input
            type="search"
            placeholder="Rechercher…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="search-input"
          />
          <PeriodSelector value={period} onChange={setPeriod} />
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {loading && <div className="loading-spinner" aria-label="Chargement des données…">Chargement…</div>}

      {focusMeta && (
        <div className="alert alert-info">
          Focus : <strong>{focusMeta.label}</strong> — classement trié (le meilleur en premier).
          <Link to="/drivers" className="alert-close">✕ Effacer le focus</Link>
        </div>
      )}

      <div className="couple-cards">
        {sorted.map((c) => (
          <Link key={c.assignmentId} to={`/drivers/${c.assignmentId}`} className="couple-card">
            <div className="couple-card-top">
              <div className="couple-driver">
                <div className="couple-avatar">{c.driverName.charAt(0)}</div>
                <div>
                  <strong>{c.driverName}</strong>
                  <span className="muted block">{c.licenseNumber}</span>
                </div>
              </div>
              <span className={`score-pill ${scoreClass(c.performanceScore)}`}>
                {c.performanceScore.toFixed(1)}
              </span>
            </div>
            <div className="couple-truck muted">
              🚛 {c.brand} {c.model} · {c.registration}
            </div>
            <div className="couple-stats">
              <div className="couple-stat">
                <span>Km</span>
                <b>{Math.round(c.totalKm).toLocaleString('fr-FR')}</b>
              </div>
              <div className="couple-stat">
                <span>Coût/km</span>
                <b>{c.costPerKm.toFixed(2)} €</b>
              </div>
              <div className="couple-stat">
                <span>Utilisation</span>
                <b>{c.utilizationRate.toFixed(0)}%</b>
              </div>
              <div className="couple-stat">
                <span>Conso</span>
                <b>{c.consumptionPer100Km.toFixed(1)} L</b>
              </div>
              <div className="couple-stat">
                <span>Éco</span>
                <b>{c.ecoScore.toFixed(0)}</b>
              </div>
              <div className="couple-stat">
                <span>Événements</span>
                <b>{c.riskEventsTotal}</b>
              </div>
            </div>
            <div className="couple-card-footer">
              <span className="muted">Détails →</span>
              {c.alerts?.length > 0 && (
                <span className="badge badge-red">🚨 {c.alerts.length}</span>
              )}
            </div>
          </Link>
        ))}
        {sorted.length === 0 && !error && (
          <p className="muted table-empty">Aucun couple ne correspond à la recherche</p>
        )}
      </div>

      <div className="card desktop-table">
        <div className="card-title">
          <h3>Classement des couples</h3>
          <span className="muted">Cliquez sur un en-tête pour trier</span>
        </div>
        <div className="table-scroll">
          <table className="table table-hover">
            <thead>
              <tr>
                {SORTABLE.map((s) => (
                  <th
                    key={s.key}
                    className={sortKey === s.key ? 'th-sorted' : 'th-sortable'}
                    onClick={() => toggleSort(s.key)}
                  >
                    {s.label}{sortArrow(s.key)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {sorted.map((c) => (
                <tr
                  key={c.assignmentId}
                  className="row-clickable"
                  onClick={() => navigate(`/drivers/${c.assignmentId}`)}
                >
                  <td>
                    <Link to={`/drivers/${c.assignmentId}`} className="cell-link" onClick={(e) => e.stopPropagation()}>
                      <strong>{c.driverName}</strong>
                      <span className="muted block">{c.licenseNumber}</span>
                    </Link>
                  </td>
                  <td>
                    <Link to={`/trucks/${c.truckId}`} className="cell-link" onClick={(e) => e.stopPropagation()}>
                      <strong>{c.brand} {c.model}</strong>
                      <span className="muted block">{c.registration}</span>
                    </Link>
                  </td>
                  <td>
                    <span className={`score-pill ${scoreClass(c.performanceScore)}`}>
                      {c.performanceScore.toFixed(1)}
                    </span>
                  </td>
                  <td>{Math.round(c.totalKm).toLocaleString('fr-FR')}</td>
                  <td>{c.costPerKm.toFixed(2)} €</td>
                  <td>{c.utilizationRate.toFixed(0)}%</td>
                  <td>{c.ecoScore.toFixed(0)}</td>
                  <td>{c.drivingTimeComplianceRate.toFixed(0)}%</td>
                  <td>{c.consumptionPer100Km.toFixed(1)} L</td>
                  <td>{c.riskEventsTotal}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
