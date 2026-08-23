import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import api from '../services/api'
import KpiWidget from '../components/KpiWidget'
import PeriodSelector from '../components/PeriodSelector'
import ScoreGauge from '../components/ScoreGauge'
import StatusBadge from '../components/StatusBadge'

const STATUS_GROUPS = [
  { key: 'ROULAGE', label: 'En route', icon: '🚚', tone: 'green', statuses: ['ROULAGE'], sub: 'chauffeurs sur la route' },
  { key: 'REPOS', label: 'Repos', icon: '🔵', tone: 'blue', statuses: ['REPOS'], sub: 'pause réglementaire' },
  { key: 'ARRET', label: "À l'arrêt", icon: '🛑', tone: 'orange', statuses: ['ARRET'], sub: 'arrêt / déchargement' },
  { key: 'ALERTE', label: 'Alertes / immobilisés', icon: '🚨', tone: 'red', statuses: ['ALERTE', 'IMMOBILISE'], sub: 'intervention requise' }
]

const COMPOSITE_PARTS = [
  { label: 'Éco-conduite', weight: '30%', hint: 'consommation vs référence + événements à risque' },
  { label: 'Conformité 561/2006', weight: '25%', hint: 'jours de tachygraphe conformes' },
  { label: 'Maintenance', weight: '20%', hint: 'entretiens planifiés réalisés à temps' },
  { label: 'Ponctualité', weight: '15%', hint: 'livraisons à l’heure' },
  { label: 'Km en charge', weight: '10%', hint: 'part des km parcourus en charge' }
]

function LiveTile({ icon, label, value, sub, tone, onClick, active }) {
  const clickable = !!onClick
  return (
    <button
      type="button"
      className={`ops-tile ${tone || ''}${active ? ' active' : ''}${clickable ? ' clickable' : ''}`}
      onClick={onClick}
      disabled={!clickable}
    >
      <div className="ops-tile-icon">{icon}</div>
      <div className="ops-tile-label">{label}</div>
      <div className="ops-tile-value">{value}</div>
      {sub && <div className="ops-tile-sub">{sub}</div>}
      {clickable && <span className="ops-tile-more">{active ? '▲ Fermer' : '▼ Voir'}</span>}
    </button>
  )
}

export default function Dashboard() {
  const navigate = useNavigate()
  const [period, setPeriod] = useState('MONTH')
  const [summary, setSummary] = useState(null)
  const [vehicles, setVehicles] = useState([])
  const [selectedGroup, setSelectedGroup] = useState(null)
  const [showScore, setShowScore] = useState(false)
  const [error, setError] = useState('')
  const panelRef = useRef(null)

  useEffect(() => {
    api
      .get('/dashboard/summary', { params: { period } })
      .then((res) => setSummary(res.data))
      .catch(() => setError('Impossible de charger le tableau de bord'))
  }, [period])

  useEffect(() => {
    const load = () => {
      api
        .get('/map/vehicles')
        .then((res) => setVehicles(res.data))
        .catch((err) => console.error('Failed to load vehicles:', err))
    }
    load()
    const id = setInterval(load, 15000)
    return () => clearInterval(id)
  }, [])

  const totalLive = vehicles.length
  const moving = vehicles.filter((v) => v.status === 'ROULAGE')
  const avgSpeed = moving.length
    ? Math.round(moving.reduce((s, v) => s + v.speedKph, 0) / moving.length)
    : null

  const selectedGroupData = STATUS_GROUPS.find((g) => g.key === selectedGroup)
  const groupVehicles = selectedGroupData
    ? vehicles.filter((v) => selectedGroupData.statuses.includes(v.status))
    : []

  const toggleGroup = (key) => setSelectedGroup((cur) => (cur === key ? null : key))

  useEffect(() => {
    if (selectedGroup && panelRef.current) {
      panelRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }, [selectedGroup])

  const focusKpi = (key) => navigate(`/drivers?kpi=${key}`)

  const coupleAlerts = summary ? summary.topCouples.flatMap((c) =>
    (c.alerts || []).map((a) => ({ ...c, text: a }))
  ) : []

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Tableau de bord</h2>
          <p>Vue d'ensemble de la flotte — KPIs North Star</p>
        </div>
        <PeriodSelector value={period} onChange={setPeriod} />
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {summary && (
        <>
          <div className="card ops-card">
            <div className="card-title">
              <h3>🚦 Flotte en temps réel</h3>
              <span className="live-badge"><span className="live-dot" /> maj 15 s</span>
            </div>
            <div className="ops-grid">
              {STATUS_GROUPS.map((g) => {
                const count = vehicles.filter((v) => g.statuses.includes(v.status)).length
                return (
                  <LiveTile
                    key={g.key}
                    tone={g.tone}
                    icon={g.icon}
                    label={g.label}
                    value={totalLive ? count : '—'}
                    sub={totalLive ? g.sub : 'en direct'}
                    onClick={() => toggleGroup(g.key)}
                    active={selectedGroup === g.key}
                  />
                )
              })}
              <LiveTile tone="purple" icon="⚡" label="Vitesse moyenne" value={avgSpeed !== null ? `${avgSpeed} km/h` : '—'} sub="camions en circulation" onClick={() => navigate('/map')} />
              <LiveTile tone="default" icon="📍" label="Km parcourus" value={`${summary.totalKm.toLocaleString('fr-FR')}`} sub="période sélectionnée" onClick={() => navigate('/map')} />
              <LiveTile tone={summary.nonCompliantDrivingDays > 0 ? 'red' : 'default'} icon="⏰" label="Conduite non conforme" value={`${summary.nonCompliantDrivingDays} j`} sub="règlement 561/2006" onClick={() => navigate('/drivers')} />
            </div>

            {selectedGroupData && (
              <div className="ops-panel" ref={panelRef}>
                <div className="ops-panel-head">
                  <strong>{selectedGroupData.icon} {selectedGroupData.label} ({groupVehicles.length})</strong>
                  <button type="button" className="ops-panel-close" onClick={() => setSelectedGroup(null)} aria-label="Fermer">✕</button>
                </div>
                {groupVehicles.length === 0 ? (
                  <p className="muted table-empty">Aucun camion dans cet état actuellement</p>
                ) : (
                  <ul className="ops-vehicles">
                    {groupVehicles.map((v) => (
                      <li key={v.truckId}>
                        <StatusBadge status={v.status} />
                        <div className="ov-main">
                          <Link to={`/trucks/${v.truckId}`} className="cell-link"><strong>{v.registration}</strong></Link>
                          <span className="muted block">{v.brand} {v.model}</span>
                        </div>
                        <div className="ov-info">
                          {v.driverName && v.assignmentId ? (
                            <Link to={`/drivers/${v.assignmentId}`} className="link">{v.driverName}</Link>
                          ) : (
                            <span>Non affecté</span>
                          )}
                          {v.speedKph > 0 && <span className="ov-speed">⚡ {v.speedKph} km/h</span>}
                        </div>
                        <Link to="/map" className="link">Carte →</Link>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </div>

          <div className="north-star-grid">
            {summary.northStars?.map((w) => (
              <KpiWidget key={w.key} widget={w} onClick={() => focusKpi(w.key)} />
            ))}
          </div>

          <div className="dashboard-grid">
            <div className="card">
              <div className="card-title">
                <h3>Score global de la flotte</h3>
                <button type="button" className="btn btn-outline btn-sm" onClick={() => setShowScore((s) => !s)}>
                  {showScore ? 'Masquer le détail' : 'Décomposer le score'}
                </button>
              </div>
              <div className="score-row">
                <ScoreGauge score={summary.globalPerformanceScore} size={140} />
                <div className="score-stats">
                  <div className="score-line">
                    <StatusBadge status="ROULAGE" /> <b>{summary.vehiclesInService}</b> en service
                  </div>
                  <div className="score-line">
                    <StatusBadge status="ARRET" /> <b>{summary.vehiclesStopped}</b> à l'arrêt/repos
                  </div>
                  <div className="score-line">
                    <StatusBadge status="ALERTE" /> <b>{summary.vehiclesAlerted}</b> en alerte / immobilisés
                  </div>
                  <div className="score-line">
                    <Link to="/drivers" className="link">Voir le classement des couples →</Link>
                  </div>
                </div>
              </div>
              {showScore && (
                <div className="score-breakdown">
                  <p className="muted">Le score composite de chaque couple Chauffeur × Camion pondère 5 indicateurs :</p>
                  <ul className="score-parts">
                    {COMPOSITE_PARTS.map((p) => (
                      <li key={p.label}>
                        <span className="score-part-weight">{p.weight}</span>
                        <span className="score-part-label">{p.label}</span>
                        <span className="muted">{p.hint}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>

            <div className="card">
              <div className="card-title">
                <h3>Alertes</h3>
                <span className="muted">À surveiller</span>
              </div>
              {coupleAlerts.length === 0 ? (
                <p className="muted empty-state">Aucune alerte active ✅</p>
              ) : (
                <ul className="alert-list">
                  {coupleAlerts.map((a, i) => (
                    <li key={`${a.assignmentId}-${i}`}>
                      <span className="alert-dot" />
                      <Link to={`/drivers/${a.assignmentId}`} className="alert-item">
                        <strong>{a.driverName}</strong> — {a.text}
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          <div className="card">
            <div className="card-title">
              <h3>Meilleurs couples Chauffeur × Camion</h3>
              <Link to="/drivers" className="link">Voir tous les chauffeurs →</Link>
            </div>
            <div className="table-scroll">
              <table className="table table-hover">
                <thead>
                  <tr>
                    <th>Chauffeur</th>
                    <th>Camion</th>
                    <th>Score</th>
                    <th>Km</th>
                    <th>Coût/km</th>
                    <th>Conso</th>
                    <th>Événements</th>
                    <th>Alertes</th>
                  </tr>
                </thead>
                <tbody>
                  {summary.topCouples.map((c) => (
                    <tr
                      key={c.assignmentId}
                      onClick={() => navigate(`/drivers/${c.assignmentId}`)}
                      className="row-clickable"
                      title="Ouvrir le détail"
                    >
                      <td className="cell-strong">{c.driverName}</td>
                      <td>
                        {c.brand} {c.model} <span className="muted">({c.registration})</span>
                      </td>
                      <td>
                        <span className={`score-pill ${c.performanceScore >= 75 ? 'good' : c.performanceScore >= 50 ? 'mid' : 'bad'}`}>
                          {c.performanceScore.toFixed(1)}
                        </span>
                      </td>
                      <td>{Math.round(c.totalKm).toLocaleString('fr-FR')}</td>
                      <td>{c.costPerKm.toFixed(2)} €</td>
                      <td>{c.consumptionPer100Km.toFixed(1)} L/100</td>
                      <td>{c.riskEventsTotal}</td>
                      <td>{c.alerts?.length || 0}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
