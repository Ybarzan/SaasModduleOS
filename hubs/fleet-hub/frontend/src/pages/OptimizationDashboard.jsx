import { useEffect, useState } from 'react'
import api from '../services/api'
import StatCard from '../components/StatCard'

const FLAG_MAP = {
  FR: '🇫🇷',
  DE: '🇩🇪',
  NL: '🇳🇱',
  CN: '🇨🇳',
  US: '🇺🇸',
  GB: '🇬🇧',
  BE: '🇧🇪',
  ES: '🇪🇸',
  IT: '🇮🇹',
  PT: '🇵🇹',
  MA: '🇲🇦',
  JP: '🇯🇵',
  KR: '🇰🇷',
  IN: '🇮🇳',
  BR: '🇧🇷',
  CA: '🇨🇦'
}
const getFlag = (code) => (code ? FLAG_MAP[code.toUpperCase()] || '🌍' : '🌍')

const fmtEur = (n) => `${Math.round(n || 0).toLocaleString('fr-FR')} €`

const STATUS_COLORS = { PENDING: 'orange', ACCEPTED: 'green', REJECTED: 'red' }

export default function OptimizationDashboard() {
  const [stats, setStats] = useState(null)
  const [lanes, setLanes] = useState([])
  const [recommendations, setRecommendations] = useState([])
  const [consolidations, setConsolidations] = useState([])
  const [loading, setLoading] = useState(true)
  const [analyzing, setAnalyzing] = useState(false)
  const [searching, setSearching] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [predictForm, setPredictForm] = useState({
    origin: '',
    destination: '',
    mode: '',
    weight: '',
    volume: ''
  })
  const [predictResult, setPredictResult] = useState(null)
  const [predicting, setPredicting] = useState(false)
  const [busyId, setBusyId] = useState(null)

  const load = () => {
    setLoading(true)
    Promise.all([
      api
        .get('/optimization/stats')
        .then((r) => setStats(r.data))
        .catch(() => {}),
      api
        .get('/optimization/lane-analysis')
        .then((r) => setLanes(r.data || []))
        .catch(() => {}),
      api
        .get('/optimization/recommendations')
        .then((r) => setRecommendations(r.data || []))
        .catch(() => {}),
      api
        .get('/optimization/consolidation')
        .then((r) => setConsolidations(r.data || []))
        .catch(() => {})
    ]).finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const flash = (msg) => {
    setNotice(msg)
    setTimeout(() => setNotice(''), 3000)
  }

  const analyze = async () => {
    setAnalyzing(true)
    setError('')
    try {
      await api.post('/optimization/analyze-routes')
      flash('Analyse des routes terminée')
      load()
    } catch {
      setError("Erreur lors de l'analyse des routes")
    } finally {
      setAnalyzing(false)
    }
  }

  const handlePredict = async (e) => {
    e.preventDefault()
    setError('')
    if (!predictForm.origin || !predictForm.destination) {
      setError('Origine et destination requis')
      return
    }
    setPredicting(true)
    try {
      const res = await api.post('/optimization/predict', null, {
        params: {
          origin: predictForm.origin,
          destination: predictForm.destination,
          mode: predictForm.mode || undefined,
          weight: predictForm.weight ? parseFloat(predictForm.weight) : undefined,
          volume: predictForm.volume ? parseFloat(predictForm.volume) : undefined
        }
      })
      setPredictResult(res.data)
      api.get('/optimization/recommendations').then((r) => setRecommendations(r.data || []))
    } catch {
      setError('Erreur lors de la prédiction')
    } finally {
      setPredicting(false)
    }
  }

  const acceptOptimization = async (id) => {
    setBusyId(id)
    try {
      await api.patch(`/optimization/accept/${id}`)
      flash('Recommandation acceptée')
      load()
    } catch {
      setError("Erreur lors de l'acceptation")
    } finally {
      setBusyId(null)
    }
  }

  const acceptConsolidation = async (id) => {
    setBusyId(id)
    try {
      await api.patch(`/optimization/consolidation/accept/${id}`)
      flash('Consolidation acceptée')
      load()
    } catch {
      setError("Erreur lors de l'acceptation")
    } finally {
      setBusyId(null)
    }
  }

  const findConsolidation = async () => {
    setSearching(true)
    try {
      await api.post('/optimization/consolidation')
      flash('Opportunités de consolidation trouvées')
      load()
    } catch {
      setError('Erreur lors de la recherche de consolidation')
    } finally {
      setSearching(false)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>📉 Moteur d'optimisation tarifaire</h2>
          <p>Analysez, prédisez et optimisez vos coûts d'expédition</p>
        </div>
        <div className="header-actions">
          <button className="btn btn-primary" disabled={analyzing} onClick={analyze}>
            {analyzing ? 'Analyse…' : '⚡ Analyser les routes'}
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      <div className="north-star-grid">
        <StatCard
          label="Routes analysées"
          value={stats?.totalRoutes ?? 0}
          sub={`${stats?.totalOptimizations ?? 0} optimisations`}
          icon="🛣️"
          tone="blue"
        />
        <StatCard
          label="Économies totales"
          value={fmtEur(stats?.totalSavings)}
          sub={`${fmtEur(stats?.acceptedSavings)} acceptées`}
          icon="📉"
          tone="green"
        />
        <StatCard
          label="Confiance moyenne"
          value={`${Math.round((stats?.avgConfidence ?? 0) * 100)}%`}
          sub={`${stats?.pendingOptimizations ?? 0} en attente`}
          icon="📊"
          tone="purple"
        />
        <StatCard
          label="Consolidations trouvées"
          value={stats?.totalConsolidationOpportunities ?? 0}
          sub={`${fmtEur(stats?.consolidationSavings)} économisées`}
          icon="📦"
          tone="green"
        />
      </div>

      <div className="card">
        <div className="card-title">
          <h3>🔍 Prédiction de tarif</h3>
        </div>
        <form onSubmit={handlePredict}>
          <div className="data-grid">
            <div className="form-field">
              <label>Origine *</label>
              <input
                type="text"
                maxLength={2}
                value={predictForm.origin}
                onChange={(e) =>
                  setPredictForm({ ...predictForm, origin: e.target.value.toUpperCase() })
                }
                placeholder="FR"
              />
            </div>
            <div className="form-field">
              <label>Destination *</label>
              <input
                type="text"
                maxLength={2}
                value={predictForm.destination}
                onChange={(e) =>
                  setPredictForm({ ...predictForm, destination: e.target.value.toUpperCase() })
                }
                placeholder="DE"
              />
            </div>
            <div className="form-field">
              <label>Mode</label>
              <select
                value={predictForm.mode}
                onChange={(e) => setPredictForm({ ...predictForm, mode: e.target.value })}
              >
                <option value="">Tous</option>
                <option value="SEA">Maritime</option>
                <option value="AIR">Aérien</option>
                <option value="ROAD">Routier</option>
              </select>
            </div>
            <div className="form-field">
              <label>Poids (kg)</label>
              <input
                type="number"
                step="0.1"
                min="0"
                value={predictForm.weight}
                onChange={(e) => setPredictForm({ ...predictForm, weight: e.target.value })}
                placeholder="500"
              />
            </div>
            <div className="form-field">
              <label>Volume (m³)</label>
              <input
                type="number"
                step="0.01"
                min="0"
                value={predictForm.volume}
                onChange={(e) => setPredictForm({ ...predictForm, volume: e.target.value })}
                placeholder="2.5"
              />
            </div>
          </div>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={predicting}>
              {predicting ? 'Prédiction…' : 'Prédire le tarif'}
            </button>
          </div>
        </form>

        {predictResult && (
          <div className="truck-card-grid" style={{ marginTop: 16 }}>
            <div className="couple-stat">
              <span>Coût prédit</span>
              <b>{fmtEur(predictResult.predictedCost)}</b>
            </div>
            <div className="couple-stat">
              <span>Transporteur</span>
              <b>{predictResult.recommendedCarrier || 'N/A'}</b>
            </div>
            <div className="couple-stat">
              <span>Confiance</span>
              <b>{Math.round((predictResult.confidence || 0) * 100)}%</b>
            </div>
            <div className="couple-stat">
              <span>Économie estimée</span>
              <b>{fmtEur(predictResult.savingsEstimate)}</b>
            </div>
          </div>
        )}
      </div>

      <div className="card desktop-table">
        <div className="card-title">
          <h3>🚛 Tableau de bord des lanes</h3>
        </div>
        {loading ? (
          <p className="muted table-empty">Chargement…</p>
        ) : lanes.length === 0 ? (
          <p className="muted table-empty">
            Aucune lane analysée. Lancez une analyse des routes pour commencer.
          </p>
        ) : (
          <div className="table-scroll">
            <table className="table table-hover">
              <thead>
                <tr>
                  <th>Lane</th>
                  <th>Expéditions</th>
                  <th>Meilleur transporteur</th>
                  <th>Meilleur coût</th>
                  <th>Coût max</th>
                  <th>Potentiel éco</th>
                  <th>Ponctualité</th>
                </tr>
              </thead>
              <tbody>
                {lanes.map((lane) => (
                  <tr key={`${lane.origin}-${lane.destination}`}>
                    <td className="cell-strong">
                      {getFlag(lane.origin)} {lane.origin} <span className="muted">→</span>{' '}
                      {getFlag(lane.destination)} {lane.destination}
                    </td>
                    <td>{lane.totalShipments}</td>
                    <td>{lane.bestCarrier}</td>
                    <td>{fmtEur(lane.bestCost)}</td>
                    <td className="muted">{fmtEur(lane.worstCost)}</td>
                    <td>{fmtEur(lane.potentialSavings)}</td>
                    <td>
                      <span
                        className={`badge ${lane.avgOnTimeRate >= 0.9 ? 'badge-green' : lane.avgOnTimeRate >= 0.75 ? 'badge-orange' : 'badge-red'}`}
                      >
                        {Math.round(lane.avgOnTimeRate * 100)}%
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="card">
        <div className="card-title">
          <h3>⚡ Recommandations</h3>
        </div>
        {recommendations.length === 0 ? (
          <p className="muted table-empty">
            Aucune recommandation disponible. Lancez une analyse des routes.
          </p>
        ) : (
          recommendations.map((rec) => (
            <div
              key={rec.id}
              className="couple-card-top"
              style={{
                borderBottom: '1px dashed var(--border)',
                paddingBottom: 10,
                marginBottom: 10
              }}
            >
              <div>
                <strong>
                  {getFlag(rec.origin)} {rec.origin} <span className="muted">→</span>{' '}
                  {getFlag(rec.destination)} {rec.destination}
                </strong>
                <span
                  className={`badge badge-${STATUS_COLORS[rec.status] || 'gray'}`}
                  style={{ marginLeft: 8 }}
                >
                  {rec.status}
                </span>
                <div className="muted">
                  Mode : {rec.transportMode}
                  {rec.recommendedCarrier && ` · Transporteur : ${rec.recommendedCarrier}`} ·
                  Confiance : {Math.round(rec.confidence * 100)}%
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <div>
                  <b>{fmtEur(rec.predictedCost)}</b>
                </div>
                <div className="muted">
                  -{rec.savingsPercent.toFixed(0)}% ({fmtEur(rec.savingsEstimate)})
                </div>
                {rec.status === 'PENDING' && (
                  <button
                    className="btn btn-outline btn-sm"
                    disabled={busyId === rec.id}
                    onClick={() => acceptOptimization(rec.id)}
                    style={{ marginTop: 6 }}
                  >
                    Accepter
                  </button>
                )}
              </div>
            </div>
          ))
        )}
      </div>

      <div className="card">
        <div className="card-title">
          <h3>📦 Consolidation d'expéditions</h3>
          <button
            className="btn btn-outline btn-sm"
            disabled={searching}
            onClick={findConsolidation}
          >
            {searching ? 'Recherche…' : '🔎 Rechercher'}
          </button>
        </div>
        {consolidations.length === 0 ? (
          <p className="muted table-empty">Aucune opportunité de consolidation trouvée.</p>
        ) : (
          consolidations.map((c) => (
            <div
              key={c.id}
              className="couple-card-top"
              style={{
                borderBottom: '1px dashed var(--border)',
                paddingBottom: 10,
                marginBottom: 10
              }}
            >
              <div>
                <strong>
                  {getFlag(c.origin)} {c.origin} <span className="muted">→</span>{' '}
                  {getFlag(c.destination)} {c.destination}
                </strong>
                <span
                  className={`badge badge-${STATUS_COLORS[c.status] || 'gray'}`}
                  style={{ marginLeft: 8 }}
                >
                  {c.status}
                </span>
                <div className="muted">
                  {c.shipmentCount} expéd. · {c.totalWeightKg} kg
                  {c.transportMode ? ` · ${c.transportMode}` : ''} · Fenêtre :{' '}
                  {c.consolidationWindowDays}j
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <div>
                  <b>{fmtEur(c.consolidatedCost)}</b>
                </div>
                <div className="muted" style={{ textDecoration: 'line-through' }}>
                  {fmtEur(c.combinedCost)}
                </div>
                <div className="muted">
                  -{c.savingsPercent.toFixed(0)}% ({fmtEur(c.estimatedSavings)})
                </div>
                {c.status === 'PENDING' && (
                  <button
                    className="btn btn-outline btn-sm"
                    disabled={busyId === c.id}
                    onClick={() => acceptConsolidation(c.id)}
                    style={{ marginTop: 6 }}
                  >
                    Accepter
                  </button>
                )}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
