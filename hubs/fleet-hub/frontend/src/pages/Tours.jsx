import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import api from '../services/api'
import { TOUR_STATUS, downloadFile, duration, hhmm, shiftDate, todayIso } from '../tours'

function StatsStrip() {
  const [stats, setStats] = useState(null)

  useEffect(() => {
    api
      .get('/tours/stats')
      .then((res) => setStats(res.data))
      .catch(() => setStats(null))
  }, [])

  if (!stats) return null
  const tiles = [
    { label: 'Tournées (30 j)', value: stats.tours },
    {
      label: 'Arrêts réussis',
      value: `${stats.successRate}%`,
      sub: `${stats.stopsDone} / ${stats.stopsDone + stats.stopsFailed}`
    },
    { label: 'Dans le créneau', value: `${stats.onTimeRate}%` },
    { label: 'Arrêts / tournée', value: stats.avgStopsPerTour },
    { label: 'Km / arrêt', value: stats.kmPerStop },
    {
      label: 'Ruptures de froid',
      value: stats.temperatureExcursions,
      tone: stats.temperatureExcursions > 0 ? 'red' : ''
    }
  ]
  return (
    <div className="tour-stats">
      {tiles.map((t) => (
        <div key={t.label} className={'tour-stat' + (t.tone ? ` tone-${t.tone}` : '')}>
          <span>{t.label}</span>
          <b>{t.value}</b>
          {t.sub && <small className="muted">{t.sub}</small>}
        </div>
      ))}
    </div>
  )
}

function NewTourForm({ date, drivers, trucks, depots, onCreated }) {
  const [form, setForm] = useState({
    name: '',
    driverId: '',
    truckId: '',
    depotId: '',
    plannedStart: '08:00'
  })
  const [error, setError] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    try {
      const res = await api.post('/tours', {
        name: form.name,
        date,
        driverId: form.driverId || null,
        truckId: form.truckId || null,
        depotId: form.depotId || null,
        plannedStart: form.plannedStart || null
      })
      onCreated(res.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Création impossible')
    }
  }

  return (
    <form className="card" onSubmit={submit}>
      <div className="card-title">
        <h3>Nouvelle tournée du {new Date(`${date}T12:00:00`).toLocaleDateString('fr-FR')}</h3>
      </div>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="data-grid">
        <div className="form-field">
          <label>Nom *</label>
          <input
            value={form.name}
            onChange={set('name')}
            placeholder="Collecte matin – secteur Est"
            required
          />
        </div>
        <div className="form-field">
          <label>Départ</label>
          <input type="time" value={form.plannedStart} onChange={set('plannedStart')} />
        </div>
        <div className="form-field">
          <label>Dépôt (départ / retour)</label>
          <select value={form.depotId} onChange={set('depotId')}>
            <option value="">—</option>
            {depots.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field">
          <label>Chauffeur</label>
          <select value={form.driverId} onChange={set('driverId')}>
            <option value="">Non affecté</option>
            {drivers.map((d) => (
              <option key={d.id} value={d.id}>
                {d.firstName} {d.lastName}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field">
          <label>Véhicule</label>
          <select value={form.truckId} onChange={set('truckId')}>
            <option value="">Non affecté</option>
            {trucks.map((t) => (
              <option key={t.id} value={t.id}>
                {t.registration} · {t.brand} {t.model}
              </option>
            ))}
          </select>
        </div>
      </div>
      <div className="form-actions">
        <button type="submit" className="btn btn-primary">
          Créer et ajouter les arrêts →
        </button>
      </div>
    </form>
  )
}

export default function Tours() {
  const navigate = useNavigate()
  const [date, setDate] = useState(todayIso())
  const [tours, setTours] = useState([])
  const [drivers, setDrivers] = useState([])
  const [trucks, setTrucks] = useState([])
  const [depots, setDepots] = useState([])
  const [showForm, setShowForm] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api
      .get('/drivers')
      .then((r) => setDrivers(r.data.filter((d) => d.active !== false)))
      .catch(() => {})
    api
      .get('/trucks')
      .then((r) => setTrucks(r.data.filter((t) => t.active !== false)))
      .catch(() => {})
    api
      .get('/sites')
      .then((r) => setDepots(r.data.filter((s) => s.kind === 'DEPOT' && s.active)))
      .catch(() => {})
  }, [])

  useEffect(() => {
    setLoading(true)
    api
      .get('/tours', { params: { from: date, to: date } })
      .then((res) => setTours(res.data))
      .catch(() => setError('Impossible de charger les tournées'))
      .finally(() => setLoading(false))
  }, [date])

  const exportCsv = () =>
    downloadFile(
      api,
      '/tours/traceability.csv',
      { from: date, to: date },
      `tracabilite_${date}.csv`
    ).catch(() => setError('Export impossible'))

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Tournées</h2>
          <p>Planifiez, optimisez et suivez vos tournées multi-arrêts</p>
        </div>
        <div className="header-actions">
          <button
            className="btn btn-outline btn-sm"
            onClick={() => setDate(shiftDate(date, -1))}
            aria-label="Jour précédent"
          >
            ←
          </button>
          <input
            type="date"
            value={date}
            onChange={(e) => e.target.value && setDate(e.target.value)}
          />
          <button
            className="btn btn-outline btn-sm"
            onClick={() => setDate(shiftDate(date, 1))}
            aria-label="Jour suivant"
          >
            →
          </button>
          <button className="btn btn-outline" onClick={exportCsv}>
            Traçabilité CSV
          </button>
          <button className="btn btn-primary" onClick={() => setShowForm((v) => !v)}>
            {showForm ? 'Fermer' : '+ Nouvelle tournée'}
          </button>
        </div>
      </div>

      <StatsStrip />

      {error && <div className="alert alert-error">{error}</div>}

      {showForm && (
        <NewTourForm
          date={date}
          drivers={drivers}
          trucks={trucks}
          depots={depots}
          onCreated={(t) => navigate(`/tours/${t.id}`)}
        />
      )}

      {depots.length === 0 && (
        <div className="alert">
          Astuce : créez votre dépôt dans{' '}
          <Link to="/sites" className="link">
            Sites
          </Link>{' '}
          pour calculer les itinéraires depuis votre point de départ.
        </div>
      )}

      {loading ? (
        <div className="loading-spinner">Chargement…</div>
      ) : tours.length === 0 ? (
        <div className="card">
          <p className="muted table-empty">Aucune tournée ce jour-là.</p>
        </div>
      ) : (
        <div className="tour-cards">
          {tours.map((t) => {
            const st = TOUR_STATUS[t.status] || { label: t.status, badge: 'badge-gray' }
            const progress =
              t.stopsTotal > 0
                ? Math.round(((t.stopsDone + t.stopsFailed) / t.stopsTotal) * 100)
                : 0
            return (
              <Link key={t.id} to={`/tours/${t.id}`} className="tour-card">
                <div className="truck-card-top">
                  <div>
                    <strong>{t.name}</strong>
                    <span className="muted block">
                      Départ {hhmm(t.plannedStart)} · {t.driverName || 'Chauffeur non affecté'}
                      {t.truckRegistration ? ` · ${t.truckRegistration}` : ''}
                    </span>
                  </div>
                  <span className={`badge ${st.badge}`}>{st.label}</span>
                </div>
                <div className="truck-card-grid">
                  <div className="couple-stat">
                    <span>Arrêts</span>
                    <b>{t.stopsTotal}</b>
                  </div>
                  <div className="couple-stat">
                    <span>Distance</span>
                    <b>{t.plannedDistanceKm != null ? `${t.plannedDistanceKm} km` : '—'}</b>
                  </div>
                  <div className="couple-stat">
                    <span>Durée</span>
                    <b>{duration(t.plannedDurationMinutes)}</b>
                  </div>
                  <div className="couple-stat">
                    <span>Retards prévus</span>
                    <b className={t.stopsLate > 0 ? 'text-red' : ''}>{t.stopsLate}</b>
                  </div>
                </div>
                <div className="tour-progress" aria-label={`Avancement ${progress}%`}>
                  <span style={{ width: `${progress}%` }} />
                </div>
                <div className="couple-card-footer">
                  <span className="muted">
                    {t.stopsDone} fait(s) · {t.stopsFailed} échec(s)
                  </span>
                  <span className="link">Ouvrir →</span>
                </div>
              </Link>
            )
          })}
        </div>
      )}
    </div>
  )
}
