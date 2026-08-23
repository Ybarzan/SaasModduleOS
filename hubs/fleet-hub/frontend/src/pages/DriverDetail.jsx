import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import {
  LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer,
  BarChart, Bar, Cell, PieChart, Pie, Legend, CartesianGrid
} from 'recharts'
import api from '../services/api'
import PeriodSelector from '../components/PeriodSelector'
import ScoreGauge from '../components/ScoreGauge'
import StatCard from '../components/StatCard'

const eventColors = {
  FREINAGE_BRUSQUE: 'var(--red)',
  ACCELERATION_FORTE: 'var(--orange)',
  EXCES_VITESSE: 'var(--purple)',
  RALENTI: 'var(--primary-2)'
}

const EVENT_LABELS = {
  FREINAGE_BRUSQUE: 'Freinage brusque',
  ACCELERATION_FORTE: 'Accélération forte',
  EXCES_VITESSE: 'Excès de vitesse',
  RALENTI: 'Ralenti'
}

const COST_COLORS = ['var(--primary)', 'var(--orange)', 'var(--primary-2)', 'var(--green)', 'var(--red)', 'var(--primary-3)', 'var(--purple)']

const TAB_TRIPS = 'trips'
const TAB_TACHO = 'tacho'
const TAB_EVENTS = 'events'

function periodRange(period) {
  const now = new Date()
  const from = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0)
  if (period === 'WEEK') from.setDate(from.getDate() - 7)
  else if (period === 'MONTH') from.setMonth(from.getMonth() - 1)
  const pad = (n) => String(n).padStart(2, '0')
  const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  return { from: iso(from), to: iso(now) }
}

export default function DriverDetail() {
  const { assignmentId } = useParams()
  const [period, setPeriod] = useState('MONTH')
  const [detail, setDetail] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState(TAB_TRIPS)
  const [trips, setTrips] = useState([])
  const [tacho, setTacho] = useState([])
  const [events, setEvents] = useState([])

  useEffect(() => {
    setLoading(true)
    api
      .get(`/kpis/couples/${assignmentId}`, { params: { period } })
      .then((res) => setDetail(res.data))
      .catch(() => setError('Impossible de charger le détail'))
      .finally(() => setLoading(false))
  }, [assignmentId, period])

  const driverId = detail?.kpis?.driverId

  useEffect(() => {
    if (!driverId) return
    const range = periodRange(period)
    const params = { driverId, from: range.from, to: range.to }
    api.get('/trips', { params }).then((res) => setTrips(res.data)).catch((err) => console.error('Failed to load trips:', err))
    api.get('/tachograph-days', { params }).then((res) => setTacho(res.data)).catch((err) => console.error('Failed to load tachograph:', err))
    api.get('/driving-events', { params }).then((res) => setEvents(res.data)).catch((err) => console.error('Failed to load events:', err))
  }, [driverId, period])

  if (error) return <div className="alert alert-error">{error}</div>
  if (loading) return <div className="loading-spinner" aria-label="Chargement du détail…">Chargement…</div>
  if (!detail) return null

  const k = detail.kpis
  const trend = detail.dailyTrend.map((d) => ({
    ...d,
    date: d.date.slice(5)
  }))

  const tabs = [
    { key: TAB_TRIPS, label: 'Trajets', count: trips.length },
    { key: TAB_TACHO, label: 'Tachygraphe', count: tacho.length },
    { key: TAB_EVENTS, label: 'Événements', count: events.length }
  ]

  return (
    <div>
      <div className="page-header">
        <div>
          <Link to="/drivers" className="link">← Retour aux chauffeurs</Link>
          <h2>{k.driverName}</h2>
          <p className="muted">
            <Link to={`/trucks/${k.truckId}`} className="link">🚛 {k.brand} {k.model} · {k.registration}</Link>
            {' · '}{k.fuelType} · licence {k.licenseNumber}
          </p>
        </div>
        <PeriodSelector value={period} onChange={setPeriod} />
      </div>

      {k.alerts?.length > 0 && (
        <div className="alert alert-warning">
          <strong>Alertes ({k.alerts.length}) :</strong>
          <ul>
            {k.alerts.map((a, i) => <li key={i}>{a}</li>)}
          </ul>
        </div>
      )}

      <div className="detail-hero">
        <ScoreGauge score={k.performanceScore} size={150} />
        <div className="detail-northstar">
          <StatCard label="Coût au km" value={k.costPerKm.toFixed(2)} unit="€" icon="💰" tone="blue" />
          <StatCard label="Utilisation" value={k.utilizationRate.toFixed(0)} unit="%" icon="⏱️" tone="green" />
          <StatCard label="Maintenance" value={k.maintenanceComplianceRate.toFixed(0)} unit="%" icon="🔧" tone="purple" />
          <StatCard label="Downtime imprévu" value={k.unplannedDowntimeRate.toFixed(1)} unit="%" icon="⚠️" tone="red" />
        </div>
      </div>

      <div className="kpi-sections">
        <div className="card">
          <h3>🧑‍✈️ Conduite (chauffeur)</h3>
          <div className="kpi-grid">
            <div className="kpi-item"><span>Événements à risque</span><b>{k.riskEventsTotal}</b><small>pour {Math.round(k.totalKm)} km</small></div>
            <div className="kpi-item"><span>Risque / 1000 km</span><b>{k.riskEventsPer1000Km}</b></div>
            <div className="kpi-item"><span>Éco-conduite</span><b>{k.ecoScore.toFixed(0)}</b><small>score</small></div>
            <div className="kpi-item"><span>Temps de roulage</span><b>{k.driveTimeShare.toFixed(0)}%</b></div>
            <div className="kpi-item"><span>Ralenti</span><b>{k.idleShare.toFixed(1)}%</b></div>
            <div className="kpi-item"><span>Ponctualité</span><b>{k.onTimeRate.toFixed(0)}%</b></div>
            <div className="kpi-item"><span>Conformité 561/2006</span><b>{k.drivingTimeComplianceRate.toFixed(0)}%</b></div>
          </div>
        </div>

        <div className="card">
          <h3>🚛 Camion</h3>
          <div className="kpi-grid">
            <div className="kpi-item"><span>Consommation</span><b>{k.consumptionPer100Km.toFixed(1)} L</b><small>/100km</small></div>
            <div className="kpi-item"><span>Dérive conso</span><b className={k.consumptionDeltaPct > 10 ? 'text-danger' : ''}>{k.consumptionDeltaPct > 0 ? '+' : ''}{k.consumptionDeltaPct.toFixed(0)}%</b><small>vs référence</small></div>
            <div className="kpi-item"><span>Disponibilité</span><b>{k.truckUptimeRate.toFixed(0)}%</b><small>uptime</small></div>
            <div className="kpi-item"><span>Immobilisation imprévue</span><b>{k.unplannedDowntimeHours} h</b></div>
            <div className="kpi-item"><span>Km en charge</span><b>{k.loadedRunRate.toFixed(0)}%</b><small>chargé</small></div>
            <div className="kpi-item"><span>Coût total</span><b>{k.totalCost.toLocaleString('fr-FR')} €</b></div>
            <div className="kpi-item"><span>Heures de roulage</span><b>{k.totalDrivingHours.toFixed(0)} h</b></div>
          </div>
        </div>
      </div>

      <div className="charts-grid">
        <div className="card">
          <h3>Kilomètres et coûts quotidiens</h3>
          <div className="chart-box">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={trend} margin={{ top: 10, right: 8, left: -14, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(148, 163, 184, 0.15)" />
                <XAxis dataKey="date" tick={{ fontSize: 11, fill: 'var(--muted)' }} interval="preserveStartEnd" minTickGap={28} stroke="var(--border-strong)" />
                <YAxis yAxisId="km" tick={{ fontSize: 11, fill: 'var(--muted)' }} stroke="var(--border-strong)" />
                <YAxis yAxisId="cost" orientation="right" tick={{ fontSize: 11, fill: 'var(--muted)' }} stroke="var(--border-strong)" width={40} />
                <Tooltip contentStyle={{ background: '#16202e', border: '1px solid #32405a', borderRadius: 10, color: '#dbe3ee' }} labelStyle={{ color: '#a9b6c6' }} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
<Line yAxisId="km" type="monotone" dataKey="km" name="Km" stroke="var(--primary)" strokeWidth={2.5} dot={false} activeDot={{ r: 4 }} />
<Line yAxisId="cost" type="monotone" dataKey="cost" name="Coût (€)" stroke="var(--primary-2)" strokeWidth={2.5} dot={false} activeDot={{ r: 4 }} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="card">
          <h3>Événements de conduite</h3>
          <div className="chart-box">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                layout="vertical"
                data={detail.eventBreakdown.map((e) => ({ ...e, label: EVENT_LABELS[e.type] || e.type }))}
                margin={{ top: 4, right: 12, left: 0, bottom: 0 }}
              >
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(148, 163, 184, 0.15)" horizontal={false} />
                <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11, fill: 'var(--muted)' }} stroke="var(--border-strong)" />
                <YAxis type="category" dataKey="label" width={120} tick={{ fontSize: 11, fill: 'var(--muted)' }} stroke="var(--border-strong)" />
                <Tooltip contentStyle={{ background: '#16202e', border: '1px solid #32405a', borderRadius: 10, color: '#dbe3ee' }} labelStyle={{ color: '#a9b6c6' }} />
                <Bar dataKey="count" name="Nombre" radius={[0, 5, 5, 0]} barSize={20}>
                  {detail.eventBreakdown.map((e) => (
                    <Cell key={e.type} fill={eventColors[e.type] || '#64748b'} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      <div className="card">
        <h3>Répartition des coûts</h3>
        <div className="cost-breakdown">
          <div className="chart-box chart-box-pie">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={detail.costBreakdown}
                  dataKey="amount"
                  nameKey="category"
                  cx="50%"
                  cy="50%"
                  outerRadius="85%"
                  paddingAngle={2}
                >
                  {detail.costBreakdown.map((c, i) => (
                    <Cell key={i} fill={COST_COLORS[i % COST_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip contentStyle={{ background: '#16202e', border: '1px solid #32405a', borderRadius: 10, color: '#dbe3ee' }} labelStyle={{ color: '#a9b6c6' }} formatter={(v) => `${v.toLocaleString('fr-FR')} €`} />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <ul className="cost-legend">
            {(() => {
              const total = detail.costBreakdown.reduce((s, c) => s + c.amount, 0)
              return detail.costBreakdown.map((c, i) => (
                <li key={i}>
                  <span className="cost-legend-dot" style={{ background: COST_COLORS[i % COST_COLORS.length] }} />
                  <span className="cost-legend-name">{c.category}</span>
                  <span className="cost-legend-val">
                    {total ? Math.round((c.amount / total) * 100) : 0}% · {c.amount.toLocaleString('fr-FR')} €
                  </span>
                </li>
              ))
            })()}
          </ul>
        </div>
      </div>

      <div className="card">
        <div className="tabs">
          {tabs.map((t) => (
            <button
              key={t.key}
              className={`tab ${activeTab === t.key ? 'active' : ''}`}
              onClick={() => setActiveTab(t.key)}
            >
              {t.label} <span className="muted">({t.count})</span>
            </button>
          ))}
        </div>

        {activeTab === TAB_TRIPS && (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Camion</th>
                  <th>Départ</th>
                  <th>Arrivée</th>
                  <th>Km</th>
                  <th>Charge</th>
                  <th>Statut</th>
                  <th>À l'heure</th>
                </tr>
              </thead>
              <tbody>
                {trips.length === 0 && <tr><td colSpan="7" className="table-empty">Aucun trajet sur la période</td></tr>}
                {trips.map((t) => (
                  <tr key={t.id}>
                    <td className="cell-strong">{t.truckRegistration}</td>
                    <td>{new Date(t.startTime).toLocaleString('fr-FR')}</td>
                    <td>{new Date(t.endTime).toLocaleString('fr-FR')}</td>
                    <td>{Math.round(t.distanceKm)}</td>
                    <td>{t.cargoWeightTons ? `${t.cargoWeightTons} t` : '—'}</td>
                    <td>{t.status === 'EN_COURS' ? 'En cours' : t.status === 'TERMINE' ? 'Terminé' : 'Annulé'}</td>
                    <td>{t.onTime ? 'Oui' : 'Non'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {activeTab === TAB_TACHO && (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Conduite (h)</th>
                  <th>Travail (h)</th>
                  <th>Repos (min)</th>
                  <th>Conforme 561/2006</th>
                  <th>Motifs</th>
                </tr>
              </thead>
              <tbody>
                {tacho.length === 0 && <tr><td colSpan="6" className="table-empty">Aucun jour de tachygraphe sur la période</td></tr>}
                {tacho.map((d) => (
                  <tr key={d.id}>
                    <td className="cell-strong">{new Date(d.date).toLocaleDateString('fr-FR')}</td>
                    <td>{d.drivingHours}</td>
                    <td>{d.workHours}</td>
                    <td>{d.restMinutes}</td>
                    <td>
                      {d.compliant ? <span className="badge badge-green">Conforme</span> : <span className="badge badge-red">Non conforme</span>}
                    </td>
                    <td>
                      {d.reasons?.length ? <span className="muted">{d.reasons.join(' · ')}</span> : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {activeTab === TAB_EVENTS && (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Date / heure</th>
                  <th>Camion</th>
                  <th>Type</th>
                  <th>Sévérité</th>
                  <th>Vitesse</th>
                  <th>Durée</th>
                </tr>
              </thead>
              <tbody>
                {events.length === 0 && <tr><td colSpan="6" className="table-empty">Aucun événement sur la période</td></tr>}
                {events.map((e) => (
                  <tr key={e.id}>
                    <td className="cell-strong">{new Date(e.timestamp).toLocaleString('fr-FR')}</td>
                    <td>{e.truckRegistration}</td>
                    <td>{EVENT_LABELS[e.type] || e.type}</td>
                    <td>
                      <span className={`severity-dot severity-${Math.max(1, Math.min(5, Math.ceil(e.severity / 2)))}`} title={`Sévérité ${e.severity}/10`} />
                      {e.severity}/10
                    </td>
                    <td>{e.speedKph ? `${Math.round(e.speedKph)} km/h` : '—'}</td>
                    <td>{e.durationSec ? `${Math.round(e.durationSec / 60)} min` : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
