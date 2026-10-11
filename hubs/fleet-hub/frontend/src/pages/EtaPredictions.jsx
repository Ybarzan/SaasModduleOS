import { Fragment, useEffect, useState } from 'react'
import api from '../services/api'
import StatCard from '../components/StatCard'
import PredictionAccuracyChart from '../components/PredictionAccuracyChart'

const COUNTRIES = [
  { code: 'FR', name: 'France' },
  { code: 'DE', name: 'Allemagne' },
  { code: 'IT', name: 'Italie' },
  { code: 'ES', name: 'Espagne' },
  { code: 'NL', name: 'Pays-Bas' },
  { code: 'BE', name: 'Belgique' },
  { code: 'VN', name: 'Vietnam' },
  { code: 'CN', name: 'Chine' },
  { code: 'IN', name: 'Inde' },
  { code: 'JP', name: 'Japon' },
  { code: 'KR', name: 'Corée du Sud' },
  { code: 'US', name: 'États-Unis' },
  { code: 'GB', name: 'Royaume-Uni' },
  { code: 'MA', name: 'Maroc' },
  { code: 'TN', name: 'Tunisie' },
  { code: 'TR', name: 'Turquie' },
  { code: 'BR', name: 'Brésil' },
  { code: 'MX', name: 'Mexique' },
  { code: 'SG', name: 'Singapour' },
  { code: 'AE', name: 'EAU' },
  { code: 'SA', name: 'Arabie Saoudite' },
  { code: 'AU', name: 'Australie' },
  { code: 'ZA', name: 'Afrique du Sud' }
]

const MODES = [
  { value: 'SEA', label: 'Maritime', icon: '🚢' },
  { value: 'AIR', label: 'Aérien', icon: '✈️' },
  { value: 'ROAD', label: 'Route', icon: '🚛' },
  { value: 'RAIL', label: 'Rail', icon: '🚆' }
]

const RISK_FACTOR_LABELS = {
  cape_routing:
    "Route actuelle via le Cap de Bonne-Espérance (Mer Rouge fermée) — environ 10 à 14 jours de plus que l'ancienne route par Suez",
  seasonal: 'Période de forte affluence',
  congestion: 'Port très fréquenté en ce moment',
  customs: 'Délai douanier possible (pays différents)',
  weather: 'Conditions météo défavorables',
  carrier_variability: 'Historique du transporteur peu régulier'
}

const CONFIDENCE_LABELS = { HIGH: 'Fiable', MEDIUM: 'Correcte', LOW: 'Incertaine' }
const CONFIDENCE_COLORS = { HIGH: 'green', MEDIUM: 'orange', LOW: 'red' }

const getModeIcon = (mode) => MODES.find((m) => m.value === mode)?.icon || '📦'
const getCountryName = (code) => {
  const c = COUNTRIES.find((c) => c.code === code)
  return c ? `${c.name} (${code})` : code
}
const confidenceLabel = (level) => CONFIDENCE_LABELS[level?.toUpperCase()] ?? level
const parseRiskFactors = (raw) =>
  !raw
    ? []
    : raw
        .split(',')
        .map((c) => c.trim())
        .filter(Boolean)
        .map((c) => RISK_FACTOR_LABELS[c] ?? c)

const fmtDate = (s) =>
  s
    ? new Date(s).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' })
    : '—'

const CONFIDENCE_COUNT_ORDER = [
  { level: 'HIGH', label: 'Fiable', color: 'green' },
  { level: 'MEDIUM', label: 'Correcte', color: 'orange' },
  { level: 'LOW', label: 'Incertaine', color: 'red' }
]

export default function EtaPredictions() {
  const [predictions, setPredictions] = useState([])
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [form, setForm] = useState({ origin: '', destination: '', mode: 'SEA', carrierName: '' })
  const [submitting, setSubmitting] = useState(false)
  const [expandedId, setExpandedId] = useState(null)
  const [showLatestDetail, setShowLatestDetail] = useState(false)
  const [technicalDetailRowId, setTechnicalDetailRowId] = useState(null)

  const load = () => {
    setLoading(true)
    api
      .get('/eta-predictions')
      .then((res) =>
        setPredictions(Array.isArray(res.data) ? res.data : res.data.predictions || [])
      )
      .catch(() => setError('Impossible de charger les prédictions'))
      .finally(() => setLoading(false))
    api
      .get('/eta-predictions/stats')
      .then((res) => setStats(res.data))
      .catch(() => {})
  }

  useEffect(() => {
    load()
  }, [])

  const handlePredict = async (e) => {
    e.preventDefault()
    setError('')
    setNotice('')
    setSubmitting(true)
    try {
      await api.post('/eta-predictions/predict', form)
      setNotice('Prédiction générée avec succès')
      setForm({ origin: '', destination: '', mode: 'SEA', carrierName: '' })
      load()
    } catch (err) {
      setError(err.response?.data?.message || 'Erreur lors de la prédiction')
    } finally {
      setSubmitting(false)
    }
  }

  const updateActual = async (id, actualArrival) => {
    setError('')
    try {
      await api.put(`/eta-predictions/${id}/actual`, { actualArrival })
      setNotice('Arrivée réelle enregistrée')
      load()
    } catch (err) {
      setError(err.response?.data?.message || "Erreur lors de l'enregistrement")
    }
  }

  const confidenceCounts = CONFIDENCE_COUNT_ORDER.map((c) => ({
    ...c,
    count: predictions.filter((p) => p.confidenceLevel?.toUpperCase() === c.level).length
  }))
  const maxConfidenceCount = Math.max(1, ...confidenceCounts.map((c) => c.count))

  const accuracyData = predictions
    .filter((p) => p.actualDays != null)
    .slice(0, 8)
    .reverse()
    .map((p) => ({
      label: `${p.origin}→${p.destination}`,
      predicted: p.predictedDays,
      actual: p.actualDays
    }))

  const latest = predictions[0]

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Prédictions ETA</h2>
          <p>Prédiction intelligente du temps d'arrivée</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      <div className="north-star-grid">
        <StatCard label="Total prédictions" value={stats?.total ?? '—'} icon="📦" tone="blue" />
        <StatCard
          label="Précision moyenne"
          value={stats?.avgAccuracy != null ? `${Math.round(stats.avgAccuracy)}%` : '—'}
          icon="📈"
          tone="green"
        />
        <StatCard
          label="À l'heure %"
          value={stats?.onTimePercent != null ? `${Math.round(stats.onTimePercent)}%` : '—'}
          icon="✅"
          tone="green"
        />
        <StatCard
          label="Jours moyens"
          value={stats?.avgDays != null ? stats.avgDays.toFixed(1) : '—'}
          icon="⏱"
          tone="purple"
        />
      </div>

      <div className="card">
        <div className="card-title">
          <h3>Nouvelle prédiction</h3>
        </div>
        <form onSubmit={handlePredict}>
          <div className="data-grid">
            <div className="form-field">
              <label>Origine *</label>
              <select
                value={form.origin}
                required
                onChange={(e) => setForm({ ...form, origin: e.target.value })}
              >
                <option value="">Sélectionner…</option>
                {COUNTRIES.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.name} ({c.code})
                  </option>
                ))}
              </select>
            </div>
            <div className="form-field">
              <label>Destination *</label>
              <select
                value={form.destination}
                required
                onChange={(e) => setForm({ ...form, destination: e.target.value })}
              >
                <option value="">Sélectionner…</option>
                {COUNTRIES.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.name} ({c.code})
                  </option>
                ))}
              </select>
            </div>
            <div className="form-field">
              <label>Mode *</label>
              <select
                value={form.mode}
                required
                onChange={(e) => setForm({ ...form, mode: e.target.value })}
              >
                {MODES.map((m) => (
                  <option key={m.value} value={m.value}>
                    {m.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-field">
              <label>Nom transporteur (optionnel)</label>
              <input
                type="text"
                value={form.carrierName}
                onChange={(e) => setForm({ ...form, carrierName: e.target.value })}
                placeholder="MSC, Maersk…"
              />
            </div>
          </div>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Prédiction…' : 'Prédire ETA'}
            </button>
          </div>
        </form>
      </div>

      {latest && (
        <div className="card">
          <div className="card-title">
            <h3>Dernière prédiction</h3>
          </div>
          <div className="couple-card-top">
            <div>
              <strong style={{ fontSize: 15 }}>
                {getModeIcon(latest.mode)} {getCountryName(latest.origin)} →{' '}
                {getCountryName(latest.destination)}
              </strong>
              <span className="muted block">
                {MODES.find((m) => m.value === latest.mode)?.label || latest.mode}
                {latest.carrierName ? ` — ${latest.carrierName}` : ''}
              </span>
            </div>
            <strong style={{ fontSize: 20 }}>{fmtDate(latest.predictedArrival)}</strong>
          </div>
          <div className="truck-card-grid">
            <div className="couple-stat">
              <span>Jours prédits</span>
              <b>{latest.predictedDays}</b>
            </div>
            <div className="couple-stat">
              <span>Jours de base</span>
              <b>{latest.baselineDays}</b>
            </div>
            <div className="couple-stat">
              <span>Variance</span>
              <b className={latest.varianceDays > 0 ? 'text-danger' : ''}>
                {latest.varianceDays > 0 ? '+' : ''}
                {latest.varianceDays} jours
              </b>
            </div>
            <div className="couple-stat">
              <span>Niveau de confiance</span>
              <b>
                <span
                  className={`badge badge-${CONFIDENCE_COLORS[latest.confidenceLevel?.toUpperCase()] || 'gray'}`}
                >
                  {confidenceLabel(latest.confidenceLevel)}
                </span>{' '}
                {latest.confidencePercent}%
              </b>
            </div>
          </div>

          <div className="progress-track" style={{ marginBottom: 16 }}>
            <div
              className={`progress-fill ${latest.confidenceLevel?.toUpperCase() === 'LOW' ? 'crit' : latest.confidenceLevel?.toUpperCase() === 'MEDIUM' ? 'warn' : ''}`}
              style={{ width: `${latest.confidencePercent}%` }}
            />
          </div>

          {parseRiskFactors(latest.riskFactors).length > 0 && (
            <div className="alert alert-warning">
              <strong>Ce qui peut affecter ce délai :</strong>
              <ul>
                {parseRiskFactors(latest.riskFactors).map((label, i) => (
                  <li key={i}>{label}</li>
                ))}
              </ul>
            </div>
          )}

          <button
            type="button"
            className="link"
            style={{ background: 'none', border: 'none', cursor: 'pointer' }}
            onClick={() => setShowLatestDetail((v) => !v)}
          >
            {showLatestDetail ? '↑ Masquer le détail technique' : '↓ Voir le détail technique'}
          </button>

          {showLatestDetail && (
            <div className="truck-card-grid" style={{ marginTop: 12 }}>
              <div className="couple-stat">
                <span>Baseline</span>
                <b>{latest.baselineDays} jours</b>
              </div>
              <div className="couple-stat">
                <span>Saisonnier</span>
                <b>
                  {latest.seasonalFactor > 0 ? '+' : ''}
                  {latest.seasonalFactor}%
                </b>
              </div>
              <div className="couple-stat">
                <span>Congestion</span>
                <b>
                  {latest.congestionFactor > 0 ? '+' : ''}
                  {latest.congestionFactor}%
                </b>
              </div>
              <div className="couple-stat">
                <span>Douane</span>
                <b>+{latest.customsDelayDays} jours</b>
              </div>
              <div className="couple-stat">
                <span>Météo</span>
                <b>+{latest.weatherDelayDays} jours</b>
              </div>
            </div>
          )}
        </div>
      )}

      {predictions.length > 0 && (
        <div className="card">
          <div className="card-title">
            <h3>📊 Visualisation</h3>
          </div>
          <div className="dashboard-grid">
            <div>
              <p className="muted">Jours prédits vs jours réels</p>
              {accuracyData.length > 0 ? (
                <PredictionAccuracyChart data={accuracyData} />
              ) : (
                <p className="muted table-empty">
                  Enregistrez une arrivée réelle sur une prédiction pour voir la précision du modèle
                  ici.
                </p>
              )}
            </div>
            <div>
              <p className="muted">Répartition par fiabilité</p>
              {confidenceCounts.map((c) => (
                <div key={c.level} style={{ marginBottom: 14 }}>
                  <div className="couple-card-footer">
                    <span>{c.label}</span>
                    <b>{c.count}</b>
                  </div>
                  <div className="progress-track">
                    <div
                      className="progress-fill"
                      style={{
                        width: `${(c.count / maxConfidenceCount) * 100}%`,
                        background: `var(--${c.color})`
                      }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {loading && (
        <div className="loading-spinner" aria-label="Chargement des prédictions…">
          Chargement…
        </div>
      )}

      <div className="card desktop-table">
        <div className="card-title">
          <h3>Historique des prédictions</h3>
        </div>
        <div className="table-scroll">
          <table className="table table-hover">
            <thead>
              <tr>
                <th>Lane</th>
                <th>Mode</th>
                <th>Transporteur</th>
                <th>Arrivée prédite</th>
                <th>Jours</th>
                <th>Confiance</th>
                <th>Précision</th>
                <th>À l'heure ?</th>
              </tr>
            </thead>
            <tbody>
              {predictions.map((pred) => {
                const isExpanded = expandedId === pred.id
                return (
                  <Fragment key={pred.id}>
                    <tr
                      className="row-clickable"
                      onClick={() => setExpandedId(isExpanded ? null : pred.id)}
                    >
                      <td className="cell-strong">
                        {pred.origin} → {pred.destination}
                      </td>
                      <td>
                        {getModeIcon(pred.mode)} {pred.mode}
                      </td>
                      <td className="muted">{pred.carrierName || '—'}</td>
                      <td>{fmtDate(pred.predictedArrival)}</td>
                      <td>{pred.predictedDays}</td>
                      <td>
                        <span
                          className={`badge badge-${CONFIDENCE_COLORS[pred.confidenceLevel?.toUpperCase()] || 'gray'}`}
                        >
                          {confidenceLabel(pred.confidenceLevel)}
                        </span>
                      </td>
                      <td className="muted">
                        {pred.predictionAccuracy != null
                          ? `${Math.round(pred.predictionAccuracy)}%`
                          : '—'}
                      </td>
                      <td>
                        {pred.isOnTime === true ? '✅' : pred.isOnTime === false ? '❌' : '—'}
                      </td>
                    </tr>
                    {isExpanded && (
                      <tr>
                        <td colSpan="8">
                          {parseRiskFactors(pred.riskFactors).length > 0 && (
                            <div className="alert alert-warning">
                              <strong>Ce qui peut affecter ce délai :</strong>
                              <ul>
                                {parseRiskFactors(pred.riskFactors).map((label, i) => (
                                  <li key={i}>{label}</li>
                                ))}
                              </ul>
                            </div>
                          )}
                          <button
                            type="button"
                            className="link"
                            style={{ background: 'none', border: 'none', cursor: 'pointer' }}
                            onClick={() =>
                              setTechnicalDetailRowId(
                                technicalDetailRowId === pred.id ? null : pred.id
                              )
                            }
                          >
                            {technicalDetailRowId === pred.id
                              ? '↑ Masquer le détail technique'
                              : '↓ Voir le détail technique'}
                          </button>
                          {technicalDetailRowId === pred.id && (
                            <div className="truck-card-grid" style={{ marginTop: 10 }}>
                              <div className="couple-stat">
                                <span>Jours baseline</span>
                                <b>{pred.baselineDays} jours</b>
                              </div>
                              <div className="couple-stat">
                                <span>Saisonnier</span>
                                <b>
                                  {pred.seasonalFactor > 0 ? '+' : ''}
                                  {pred.seasonalFactor}%
                                </b>
                              </div>
                              <div className="couple-stat">
                                <span>Congestion</span>
                                <b>
                                  {pred.congestionFactor > 0 ? '+' : ''}
                                  {pred.congestionFactor}%
                                </b>
                              </div>
                              <div className="couple-stat">
                                <span>Douane</span>
                                <b>+{pred.customsDelayDays} jours</b>
                              </div>
                              <div className="couple-stat">
                                <span>Météo</span>
                                <b>+{pred.weatherDelayDays} jours</b>
                              </div>
                            </div>
                          )}
                          {pred.actualArrival ? (
                            <p className="muted" style={{ marginTop: 10 }}>
                              Arrivée réelle : <strong>{fmtDate(pred.actualArrival)}</strong>
                            </p>
                          ) : (
                            <div className="form-field" style={{ marginTop: 10, maxWidth: 220 }}>
                              <label>Enregistrer l'arrivée réelle</label>
                              <input
                                type="date"
                                onChange={(e) => {
                                  if (e.target.value) updateActual(pred.id, e.target.value)
                                }}
                              />
                            </div>
                          )}
                        </td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
              {predictions.length === 0 && !loading && (
                <tr>
                  <td colSpan="8" className="table-empty">
                    Aucune prédiction pour le moment
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
