import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  LineChart, Line, XAxis, YAxis, Tooltip, Legend, ResponsiveContainer,
  PieChart, Pie, Cell, CartesianGrid
} from 'recharts'
import api from '../services/api'
import PeriodSelector from '../components/PeriodSelector'
import StatCard from '../components/StatCard'
import StatusBadge from '../components/StatusBadge'

const EVENT_LABELS = {
  FREINAGE_BRUSQUE: 'Freinage brusque',
  ACCELERATION_FORTE: 'Accélération forte',
  EXCES_VITESSE: 'Excès de vitesse',
  RALENTI: 'Ralenti'
}

const MAINT_LABELS = {
  VIDANGE: 'Vidange',
  FREINS: 'Freins',
  PNEUS: 'Pneus',
  REVISION: 'Révision',
  CONTROLE_TECHNIQUE: 'Contrôle technique',
  REPARATION: 'Réparation'
}

const COST_COLORS = ['var(--primary)', 'var(--orange)', 'var(--primary-2)', 'var(--green)', 'var(--red)', 'var(--primary-3)', 'var(--purple)']

const TAB_TRIPS = 'trips'
const TAB_FUEL = 'fuel'
const TAB_MAINT = 'maint'
const TAB_EVENTS = 'events'

function statusTone(status) {
  if (status === 'TERMINE') return 'badge-green'
  if (status === 'EN_COURS') return 'badge-blue'
  return 'badge-red'
}

export default function TruckDetail() {
  const { truckId } = useParams()
  const [period, setPeriod] = useState('MONTH')
  const [detail, setDetail] = useState(null)
  const [error, setError] = useState('')
  const [activeTab, setActiveTab] = useState(TAB_TRIPS)

  useEffect(() => {
    api
      .get(`/kpis/trucks/${truckId}`, { params: { period } })
      .then((res) => setDetail(res.data))
      .catch(() => setError('Impossible de charger le détail du camion'))
  }, [truckId, period])

  const trend = useMemo(
    () => (detail ? detail.dailyTrend.map((d) => ({ ...d, date: d.date.slice(5) })) : []),
    [detail]
  )

  if (error) return <div className="alert alert-error">{error}</div>
  if (!detail) return <p className="muted">Chargement…</p>

  const k = detail.kpis

  const totalByTab = {
    [TAB_TRIPS]: detail.trips.length,
    [TAB_FUEL]: detail.fuels.length,
    [TAB_MAINT]: detail.maintenance.length,
    [TAB_EVENTS]: detail.events.length
  }

  const tabs = [
    { key: TAB_TRIPS, label: 'Trajets' },
    { key: TAB_FUEL, label: 'Carburant' },
    { key: TAB_MAINT, label: 'Maintenance' },
    { key: TAB_EVENTS, label: 'Événements' }
  ]

  const totalCost = detail.costBreakdown.reduce((s, c) => s + c.amount, 0)

  return (
    <div>
      <div className="page-header">
        <div>
          <Link to="/trucks" className="link">← Retour aux camions</Link>
          <h2>🚛 {k.brand} {k.model}</h2>
          <p className="muted">
            {k.registration} · {k.truckType.toLowerCase()} · {k.fuelType === 'DIESEL' ? 'Diesel' : 'Électrique'}
            {k.modelYear ? ` · ${k.modelYear}` : ''}
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
        <div className="truck-hero-badge">
          <StatusBadge status={k.currentStatus} />
          <span className="muted block" style={{ marginTop: 4 }}>statut actuel</span>
        </div>
        <div className="detail-northstar">
          <StatCard label="Consommation" value={k.consumptionPer100Km.toFixed(1)} unit="L/100" icon="⛽" tone="blue" />
          <StatCard label="Maintenance" value={k.maintenanceComplianceRate.toFixed(0)} unit="%" icon="🔧" tone="purple" />
          <StatCard label="Disponibilité" value={k.truckUptimeRate.toFixed(0)} unit="%" icon="🟢" tone="green" />
          <StatCard label="Coût au km" value={k.costPerKm.toFixed(2)} unit="€" icon="💰" tone="red" />
        </div>
      </div>

      <div className="kpi-sections">
        <div className="card">
          <h3>🚛 Camion</h3>
          <div className="kpi-grid">
            <div className="kpi-item"><span>Consommation</span><b>{k.consumptionPer100Km.toFixed(1)} L</b><small>/100km</small></div>
            <div className="kpi-item"><span>Dérive conso</span><b className={k.consumptionDeltaPct > 10 ? 'text-danger' : ''}>{k.consumptionDeltaPct > 0 ? '+' : ''}{k.consumptionDeltaPct.toFixed(0)}%</b><small>vs référence</small></div>
            <div className="kpi-item"><span>Disponibilité</span><b>{k.truckUptimeRate.toFixed(0)}%</b><small>uptime</small></div>
            <div className="kpi-item"><span>Immobilisation imprévue</span><b>{k.unplannedDowntimeHours} h</b></div>
            <div className="kpi-item"><span>Utilisation</span><b>{k.utilizationRate.toFixed(0)}%</b><small>temps en service</small></div>
            <div className="kpi-item"><span>Km en charge</span><b>{k.loadedRunRate.toFixed(0)}%</b><small>chargé</small></div>
          </div>
        </div>

        <div className="card">
          <h3>📈 Activité sur la période</h3>
          <div className="kpi-grid">
            <div className="kpi-item"><span>Kilomètres</span><b>{Math.round(k.totalKm).toLocaleString('fr-FR')}</b><small>{k.daysInPeriod} jours</small></div>
            <div className="kpi-item"><span>Heures de roulage</span><b>{k.totalDrivingHours.toFixed(0)} h</b></div>
            <div className="kpi-item"><span>Trajets</span><b>{k.tripCount}</b></div>
            <div className="kpi-item"><span>Événements à risque</span><b>{k.eventCount}</b></div>
            <div className="kpi-item"><span>Coût total</span><b>{k.totalCost.toLocaleString('fr-FR')} €</b></div>
            <div className="kpi-item">
              <span>Chauffeur</span>
              {k.driverId && k.assignmentId ? (
                <Link to={`/drivers/${k.assignmentId}`} className="link">{k.driverName}</Link>
              ) : (
                <b className="muted">Non affecté</b>
              )}
              <small>affectation active</small>
            </div>
          </div>
        </div>
      </div>

      <div className="charts-grid">
        <div className="card">
          <h3>Kilomètres et carburant quotidiens</h3>
          <div className="chart-box">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={trend} margin={{ top: 10, right: 8, left: -14, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(148, 163, 184, 0.15)" />
                <XAxis dataKey="date" tick={{ fontSize: 11, fill: 'var(--muted)' }} interval="preserveStartEnd" minTickGap={28} stroke="var(--border-strong)" />
                <YAxis yAxisId="km" tick={{ fontSize: 11, fill: 'var(--muted)' }} stroke="var(--border-strong)" />
                <YAxis yAxisId="fuel" orientation="right" tick={{ fontSize: 11, fill: 'var(--muted)' }} stroke="var(--border-strong)" width={40} />
                <Tooltip contentStyle={{ background: '#16202e', border: '1px solid #32405a', borderRadius: 10, color: '#dbe3ee' }} labelStyle={{ color: '#a9b6c6' }} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
<Line yAxisId="km" type="monotone" dataKey="km" name="Km" stroke="var(--primary)" strokeWidth={2.5} dot={false} activeDot={{ r: 4 }} />
<Line yAxisId="fuel" type="monotone" dataKey="liters" name="Litres" stroke="var(--orange)" strokeWidth={2.5} dot={false} activeDot={{ r: 4 }} />
              </LineChart>
            </ResponsiveContainer>
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
              {detail.costBreakdown.map((c, i) => (
                <li key={i}>
                  <span className="cost-legend-dot" style={{ background: COST_COLORS[i % COST_COLORS.length] }} />
                  <span className="cost-legend-name">{c.category.toLowerCase()}</span>
                  <span className="cost-legend-val">
                    {totalCost ? Math.round((c.amount / totalCost) * 100) : 0}% · {c.amount.toLocaleString('fr-FR')} €
                  </span>
                </li>
              ))}
            </ul>
          </div>
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
              {t.label} <span className="muted">({totalByTab[t.key]})</span>
            </button>
          ))}
        </div>

        {activeTab === TAB_TRIPS && (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Chauffeur</th>
                  <th>Départ</th>
                  <th>Arrivée</th>
                  <th>Km</th>
                  <th>Charge</th>
                  <th>Statut</th>
                  <th>À l'heure</th>
                </tr>
              </thead>
              <tbody>
                {detail.trips.length === 0 && <tr><td colSpan="7" className="table-empty">Aucun trajet sur la période</td></tr>}
                {detail.trips.map((t) => (
                  <tr key={t.id}>
                    <td className="cell-strong">{t.driverName}</td>
                    <td>{new Date(t.startTime).toLocaleString('fr-FR')}</td>
                    <td>{new Date(t.endTime).toLocaleString('fr-FR')}</td>
                    <td>{Math.round(t.distanceKm)}</td>
                    <td>{t.cargoWeightTons ? `${t.cargoWeightTons} t` : '—'}</td>
                    <td><span className={`badge ${statusTone(t.status)}`}>{t.status === 'EN_COURS' ? 'En cours' : t.status === 'TERMINE' ? 'Terminé' : 'Annulé'}</span></td>
                    <td>{t.onTime ? 'Oui' : 'Non'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {activeTab === TAB_FUEL && (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Litres</th>
                  <th>Montant (€)</th>
                  <th>Kilométrage</th>
                  <th>Prix / L</th>
                </tr>
              </thead>
              <tbody>
                {detail.fuels.length === 0 && <tr><td colSpan="5" className="table-empty">Aucun relevé de carburant sur la période</td></tr>}
                {detail.fuels.map((f) => (
                  <tr key={f.id}>
                    <td className="cell-strong">{new Date(f.date).toLocaleDateString('fr-FR')}</td>
                    <td>{f.liters.toLocaleString('fr-FR')} L</td>
                    <td>{f.amount.toLocaleString('fr-FR')} €</td>
                    <td>{f.odometerKm ? `${Math.round(f.odometerKm).toLocaleString('fr-FR')} km` : '—'}</td>
                    <td>{f.liters > 0 ? `${(f.amount / f.liters).toFixed(3)} €` : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {activeTab === TAB_MAINT && (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Planifié le</th>
                  <th>Réalisé le</th>
                  <th>Prévu</th>
                  <th>À temps</th>
                  <th>Coût</th>
                </tr>
              </thead>
              <tbody>
                {detail.maintenance.length === 0 && <tr><td colSpan="6" className="table-empty">Aucune maintenance sur la période</td></tr>}
                {detail.maintenance.map((m) => (
                  <tr key={m.id}>
                    <td className="cell-strong">{MAINT_LABELS[m.type] || m.type}</td>
                    <td>{new Date(m.scheduledDate).toLocaleDateString('fr-FR')}</td>
                    <td>{m.doneDate ? new Date(m.doneDate).toLocaleDateString('fr-FR') : '—'}</td>
                    <td>{m.planned ? 'Oui' : 'Non'}</td>
                    <td>{m.doneOnTime ? <span className="badge badge-green">Oui</span> : <span className="badge badge-red">Non</span>}</td>
                    <td>{m.cost ? `${m.cost.toLocaleString('fr-FR')} €` : '—'}</td>
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
                  <th>Chauffeur</th>
                  <th>Type</th>
                  <th>Sévérité</th>
                  <th>Vitesse</th>
                </tr>
              </thead>
              <tbody>
                {detail.events.length === 0 && <tr><td colSpan="5" className="table-empty">Aucun événement sur la période</td></tr>}
                {detail.events.map((e) => (
                  <tr key={e.id}>
                    <td className="cell-strong">{new Date(e.timestamp).toLocaleString('fr-FR')}</td>
                    <td>{e.driverName}</td>
                    <td>{EVENT_LABELS[e.type] || e.type}</td>
                    <td>
                      <span className={`severity-dot severity-${Math.max(1, Math.min(5, Math.ceil(e.severity / 2)))}`} title={`Sévérité ${e.severity}/10`} />
                      {e.severity}/10
                    </td>
                    <td>{e.speedKph ? `${Math.round(e.speedKph)} km/h` : '—'}</td>
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
