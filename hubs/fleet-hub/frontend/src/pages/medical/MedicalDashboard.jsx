import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts'
import api from '../../services/api'
import StatCard from '../../components/StatCard'
import { MEDICAL_PRODUCTS, NC_TYPES, minutesLabel } from '../../medical'
import { todayIso, shiftDate } from '../../tours'

const PERIODS = [
  { days: 7, label: '7 jours' },
  { days: 30, label: '30 jours' },
  { days: 90, label: '90 jours' }
]

/** Tableau de bord de l'activité Transport médical. */
export default function MedicalDashboard() {
  const [days, setDays] = useState(30)
  const [data, setData] = useState(null)
  const [integrity, setIntegrity] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    const to = todayIso()
    const from = shiftDate(to, -(days - 1))
    api
      .get('/medical/dashboard', { params: { from, to } })
      .then((res) => setData(res.data))
      .catch(() => setError('Chargement des indicateurs impossible'))
  }, [days])

  useEffect(() => {
    api
      .get('/medical/custody/verify')
      .then((res) => setIntegrity(res.data))
      .catch(() => setIntegrity(null))
  }, [])

  if (!data) {
    return error ? (
      <div className="alert alert-error">{error}</div>
    ) : (
      <div className="loading-spinner">Chargement…</div>
    )
  }

  const daily = data.daily.map((d) => ({
    ...d,
    label: new Date(`${d.date}T12:00:00`).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })
  }))
  const maxCategory = Math.max(1, ...Object.values(data.collectedByCategory))

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Transport médical</h2>
          <p>Collectes, remises au laboratoire, chaîne du froid et non-conformités</p>
        </div>
        <div className="segmented" role="radiogroup" aria-label="Période">
          {PERIODS.map((p) => (
            <button
              key={p.days}
              type="button"
              role="radio"
              aria-checked={days === p.days}
              className={days === p.days ? 'active' : ''}
              onClick={() => setDays(p.days)}
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>

      {integrity && (
        <div className={'integrity-banner ' + (integrity.valid ? 'ok' : 'ko')} role="status">
          {integrity.valid ? (
            <>
              🔒 Journal de garde intègre — {integrity.eventsChecked} événement(s) vérifié(s) par
              chaînage d'empreintes.
            </>
          ) : (
            <>
              ⚠ Intégrité du journal rompue à l'événement n° {integrity.firstInvalidSequence} :
              une donnée a été modifiée hors de l'application.
            </>
          )}{' '}
          <Link to="/tracabilite" className="link">
            Voir le journal →
          </Link>
        </div>
      )}

      <div className="dashboard-grid medical-kpis">
        <StatCard
          icon="🧪"
          tone="blue"
          label="Produits collectés"
          value={data.productsCollected}
          sub={`${data.productsDelivered} remis au laboratoire`}
        />
        <StatCard
          icon="✅"
          tone="green"
          label="Tournées conformes"
          value={data.conformityRate}
          unit="%"
          sub={`${data.toursCompleted} tournée(s) terminée(s) sur ${data.tours}`}
        />
        <StatCard
          icon="⚠"
          tone={data.openNonConformities > 0 ? 'red' : 'green'}
          label="Non-conformités ouvertes"
          value={data.openNonConformities}
          sub="à traiter (toutes périodes)"
        />
        <StatCard
          icon="❄"
          tone={data.temperatureExcursions > 0 ? 'red' : 'green'}
          label="Ruptures du froid"
          value={data.temperatureExcursions}
          sub="relevés hors plage"
        />
        <StatCard
          icon="⏱"
          tone={data.transitsExceeded > 0 ? 'red' : 'purple'}
          label="Délai collecte → labo"
          value={minutesLabel(data.averageTransitMinutes != null ? Math.round(data.averageTransitMinutes) : null)}
          sub={`max ${minutesLabel(data.maxTransitMinutes)} · limite ${minutesLabel(data.transitLimitMinutes)}`}
        />
        <StatCard
          icon="📍"
          tone={data.collectionsFailed > 0 ? 'red' : 'green'}
          label="Collectes réalisées"
          value={data.collectionsDone}
          sub={`${data.collectionsFailed} collecte(s) non réalisée(s)`}
        />
      </div>

      <div className="charts-grid">
        <div className="card">
          <div className="card-title">
            <h3>Produits collectés et remis par jour</h3>
          </div>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={daily} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#8884" vertical={false} />
              <XAxis dataKey="label" tick={{ fontSize: 11, fill: '#8a8a8a' }} interval="preserveStartEnd" />
              <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#8a8a8a' }} />
              <Tooltip
                contentStyle={{ background: 'var(--card)', border: '1px solid var(--border)', borderRadius: 8 }}
              />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              {/* Couleurs en dur : un attribut SVG fill ne résout pas var(--…) */}
              <Bar dataKey="collected" name="Collectés" fill="#2f8f9d" radius={[3, 3, 0, 0]} />
              <Bar dataKey="delivered" name="Remis" fill="#7fa35a" radius={[3, 3, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="card">
          <div className="card-title">
            <h3>Collectes par catégorie</h3>
          </div>
          <ul className="category-bars">
            {MEDICAL_PRODUCTS.map((p) => {
              const v = data.collectedByCategory[p.value] || 0
              return (
                <li key={p.value}>
                  <span className="cb-label">
                    <span aria-hidden="true">{p.icon}</span> {p.label}
                  </span>
                  <span className="cb-track">
                    <span style={{ width: `${(v / maxCategory) * 100}%` }} />
                  </span>
                  <b>{v}</b>
                </li>
              )
            })}
          </ul>
          <div className="card-title" style={{ marginTop: '1.25rem' }}>
            <h3>Non-conformités de la période</h3>
            <Link to="/non-conformites" className="link">
              Gérer →
            </Link>
          </div>
          {Object.keys(data.nonConformitiesByType).length === 0 ? (
            <p className="muted">Aucune non-conformité sur la période.</p>
          ) : (
            <ul className="nc-type-list">
              {Object.entries(data.nonConformitiesByType).map(([type, count]) => (
                <li key={type}>
                  <span>{NC_TYPES[type] || type}</span>
                  <b>{count}</b>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      <div className="card">
        <div className="card-title">
          <h3>Accès rapides</h3>
        </div>
        <div className="quick-links">
          <Link to="/tours" className="btn btn-outline">Tournées du jour</Link>
          <Link to="/planning" className="btn btn-outline">Planification</Link>
          <Link to="/sites" className="btn btn-outline">Points de collecte</Link>
          <Link to="/tracabilite" className="btn btn-outline">Journal de garde</Link>
          <Link to="/non-conformites" className="btn btn-outline">Non-conformités</Link>
        </div>
      </div>
    </div>
  )
}
