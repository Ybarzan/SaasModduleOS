import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import api from '../services/api'
import TourMap from '../components/TourMap'
import StopCompletionForm from '../components/StopCompletionForm'
import {
  STOP_STATUS,
  STOP_TYPES,
  TOUR_STATUS,
  duration,
  hhmm,
  shiftDate,
  siteKindLabel,
  stopTypeLabel
} from '../tours'

const EMPTY_STOP = {
  siteId: '',
  type: 'LIVRAISON',
  windowStart: '',
  windowEnd: '',
  serviceMinutes: '',
  temperatureMinCelsius: '',
  temperatureMaxCelsius: ''
}

function AddStopForm({ sites, onAdd }) {
  const [form, setForm] = useState(EMPTY_STOP)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const num = (v) => (v === '' ? null : Number(v))

  const submit = async (e) => {
    e.preventDefault()
    const ok = await onAdd({
      siteId: Number(form.siteId),
      type: form.type,
      windowStart: form.windowStart || null,
      windowEnd: form.windowEnd || null,
      serviceMinutes: num(form.serviceMinutes),
      temperatureMinCelsius: num(form.temperatureMinCelsius),
      temperatureMaxCelsius: num(form.temperatureMaxCelsius)
    })
    if (ok) setForm({ ...EMPTY_STOP, type: form.type })
  }

  return (
    <form onSubmit={submit}>
      <div className="data-grid">
        <div className="form-field">
          <label>Site *</label>
          <select value={form.siteId} onChange={set('siteId')} required>
            <option value="">Choisir…</option>
            {sites.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name} · {siteKindLabel(s.kind)}
                {s.city ? ` · ${s.city}` : ''}
                {s.latitude == null ? ' (non localisé)' : ''}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field">
          <label>Type</label>
          <select value={form.type} onChange={set('type')}>
            {STOP_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field">
          <label>Créneau début (défaut : site)</label>
          <input type="time" value={form.windowStart} onChange={set('windowStart')} />
        </div>
        <div className="form-field">
          <label>Créneau fin</label>
          <input type="time" value={form.windowEnd} onChange={set('windowEnd')} />
        </div>
        <div className="form-field">
          <label>Durée sur place (min)</label>
          <input
            type="number"
            min="0"
            value={form.serviceMinutes}
            onChange={set('serviceMinutes')}
          />
        </div>
        <div className="form-field">
          <label>Température min (°C)</label>
          <input
            type="number"
            step="0.1"
            value={form.temperatureMinCelsius}
            onChange={set('temperatureMinCelsius')}
          />
        </div>
        <div className="form-field">
          <label>Température max (°C)</label>
          <input
            type="number"
            step="0.1"
            value={form.temperatureMaxCelsius}
            onChange={set('temperatureMaxCelsius')}
          />
        </div>
      </div>
      <div className="form-actions">
        <button type="submit" className="btn btn-primary">
          Ajouter l'arrêt
        </button>
      </div>
    </form>
  )
}

function ProofSummary({ s }) {
  const parts = []
  if (s.signedBy) parts.push(`Signé : ${s.signedBy}`)
  if (s.parcelCount != null) parts.push(`${s.parcelCount} colis`)
  if (s.sampleCount != null) parts.push(`${s.sampleCount} échantillon(s)`)
  if (s.temperatureCelsius != null) parts.push(`${s.temperatureCelsius} °C`)
  if (s.failureReason) parts.push(`Motif : ${s.failureReason}`)
  if (s.completedAt)
    parts.push(
      new Date(s.completedAt).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })
    )
  return (
    <span className="muted block">
      {parts.join(' · ')}
      {s.temperatureExcursion && <span className="badge badge-red"> Rupture froid</span>}
    </span>
  )
}

export default function TourDetail() {
  const { tourId } = useParams()
  const navigate = useNavigate()
  const [tour, setTour] = useState(null)
  const [sites, setSites] = useState([])
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')
  const [busy, setBusy] = useState(false)
  const [closingStop, setClosingStop] = useState(null)

  const load = useCallback(
    () =>
      api
        .get(`/tours/${tourId}`)
        .then((res) => setTour(res.data))
        .catch((err) =>
          setError(err.response?.status === 404 ? 'Tournée introuvable' : 'Chargement impossible')
        ),
    [tourId]
  )

  useEffect(() => {
    load()
    api
      .get('/sites')
      .then((res) => setSites(res.data.filter((s) => s.active && s.kind !== 'DEPOT')))
      .catch(() => {})
  }, [load])

  /** Exécute une action qui renvoie la tournée mise à jour. */
  const act = async (request, message) => {
    setError('')
    setInfo('')
    setBusy(true)
    try {
      const res = await request()
      if (res?.data?.id) setTour(res.data)
      if (message) setInfo(message)
      return true
    } catch (err) {
      setError(err.response?.data?.message || 'Action impossible')
      return false
    } finally {
      setBusy(false)
    }
  }

  if (!tour) {
    return error ? (
      <div className="alert alert-error">{error}</div>
    ) : (
      <div className="loading-spinner">Chargement…</div>
    )
  }

  const st = TOUR_STATUS[tour.status] || { label: tour.status, badge: 'badge-gray' }
  const editable = tour.status === 'PLANIFIEE' || tour.status === 'EN_COURS'
  const kmBefore = tour.plannedDistanceKm

  const move = (index, delta) => {
    const ids = tour.stops.map((s) => s.id)
    const target = index + delta
    if (target < 0 || target >= ids.length) return
    ;[ids[index], ids[target]] = [ids[target], ids[index]]
    act(() => api.put(`/tours/${tour.id}/order`, { stopIds: ids }))
  }

  const optimize = () =>
    act(async () => {
      const res = await api.post(`/tours/${tour.id}/optimize`)
      const gain =
        kmBefore != null ? Math.round((kmBefore - res.data.plannedDistanceKm) * 10) / 10 : 0
      setInfo(gain > 0 ? `Itinéraire optimisé : ${gain} km économisés` : 'Itinéraire optimisé')
      return res
    })

  const duplicate = () => {
    const date = window.prompt('Dupliquer pour quelle date ? (AAAA-MM-JJ)', shiftDate(tour.date, 1))
    if (!date) return
    act(async () => {
      const res = await api.post(`/tours/${tour.id}/duplicate`, null, { params: { date } })
      navigate(`/tours/${res.data.id}`)
      return res
    })
  }

  const remove = () => {
    if (!window.confirm('Supprimer cette tournée ?')) return
    act(async () => {
      await api.delete(`/tours/${tour.id}`)
      navigate('/tours')
    })
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <Link to="/tours" className="link">
            ← Tournées
          </Link>
          <h2>
            {tour.name} <span className={`badge ${st.badge}`}>{st.label}</span>
          </h2>
          <p>
            {new Date(`${tour.date}T12:00:00`).toLocaleDateString('fr-FR', {
              weekday: 'long',
              day: 'numeric',
              month: 'long'
            })}{' '}
            · départ {hhmm(tour.plannedStart)} · {tour.driverName || 'chauffeur non affecté'}
            {tour.truckRegistration ? ` · ${tour.truckRegistration}` : ''}
            {tour.depotName ? ` · dépôt ${tour.depotName}` : ''}
          </p>
        </div>
        <div className="header-actions">
          {editable && tour.stops.length > 1 && (
            <button className="btn btn-primary" onClick={optimize} disabled={busy}>
              ⚡ Optimiser l'itinéraire
            </button>
          )}
          {tour.status === 'PLANIFIEE' && (
            <button
              className="btn btn-outline"
              onClick={() => act(() => api.post(`/tours/${tour.id}/start`))}
              disabled={busy}
            >
              Démarrer
            </button>
          )}
          {tour.status === 'EN_COURS' && (
            <button
              className="btn btn-outline"
              onClick={() => act(() => api.post(`/tours/${tour.id}/complete`))}
              disabled={busy}
            >
              Terminer
            </button>
          )}
          <button className="btn btn-outline" onClick={duplicate} disabled={busy}>
            Dupliquer
          </button>
          {editable && (
            <button
              className="btn btn-outline"
              onClick={() =>
                window.confirm('Annuler cette tournée ?') &&
                act(() => api.post(`/tours/${tour.id}/cancel`))
              }
              disabled={busy}
            >
              Annuler
            </button>
          )}
          {tour.status !== 'EN_COURS' && (
            <button className="btn btn-danger" onClick={remove} disabled={busy}>
              Supprimer
            </button>
          )}
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {info && <div className="alert">{info}</div>}

      <div className="tour-stats">
        <div className="tour-stat">
          <span>Arrêts</span>
          <b>{tour.stopsTotal}</b>
          <small className="muted">
            {tour.stopsDone} fait(s) · {tour.stopsFailed} échec(s)
          </small>
        </div>
        <div className="tour-stat">
          <span>Distance estimée</span>
          <b>{tour.plannedDistanceKm != null ? `${tour.plannedDistanceKm} km` : '—'}</b>
        </div>
        <div className="tour-stat">
          <span>Durée estimée</span>
          <b>{duration(tour.plannedDurationMinutes)}</b>
        </div>
        <div className={'tour-stat' + (tour.stopsLate > 0 ? ' tone-red' : '')}>
          <span>Hors créneau prévus</span>
          <b>{tour.stopsLate}</b>
        </div>
      </div>

      <div className="card">
        <TourMap tour={tour} />
      </div>

      <div className="card">
        <div className="card-title">
          <h3>Ordre de passage</h3>
          {tour.optimizedAt && (
            <span className="muted">
              Optimisé le {new Date(tour.optimizedAt).toLocaleString('fr-FR')}
            </span>
          )}
        </div>
        {tour.stops.length === 0 ? (
          <p className="muted table-empty">Aucun arrêt. Ajoutez des sites ci-dessous.</p>
        ) : (
          <ol className="stop-list">
            {tour.stops.map((s, i) => {
              const ss = STOP_STATUS[s.status] || { label: s.status, badge: 'badge-gray' }
              return (
                <li key={s.id} className={'stop-item status-' + s.status.toLowerCase()}>
                  <div className="stop-seq">{s.sequence}</div>
                  <div className="stop-body">
                    <div className="stop-head">
                      <strong>{s.siteName}</strong>
                      <span className={`badge ${ss.badge}`}>{ss.label}</span>
                      <span className="badge badge-blue">{stopTypeLabel(s.type)}</span>
                      {s.plannedLatenessMinutes > 0 && s.status === 'A_FAIRE' && (
                        <span className="badge badge-orange">
                          +{s.plannedLatenessMinutes} min hors créneau
                        </span>
                      )}
                    </div>
                    <span className="muted block">
                      {[s.address, s.city].filter(Boolean).join(', ') || siteKindLabel(s.siteKind)}
                      {s.latitude == null && ' · ⚠ non localisé'}
                    </span>
                    <span className="block">
                      Arrivée prévue <b>{hhmm(s.plannedArrival)}</b>
                      {(s.windowStart || s.windowEnd) &&
                        ` · créneau ${hhmm(s.windowStart)}–${hhmm(s.windowEnd)}`}
                      {` · ${s.serviceMinutes} min sur place`}
                      {(s.temperatureMinCelsius != null || s.temperatureMaxCelsius != null) &&
                        ` · ${s.temperatureMinCelsius ?? '…'}–${s.temperatureMaxCelsius ?? '…'} °C`}
                    </span>
                    {s.status !== 'A_FAIRE' && <ProofSummary s={s} />}
                    {closingStop === s.id && (
                      <StopCompletionForm
                        stop={s}
                        busy={busy}
                        onCancel={() => setClosingStop(null)}
                        onSubmit={async (payload) => {
                          const ok = await act(() =>
                            api.post(`/tours/${tour.id}/stops/${s.id}/complete`, payload)
                          )
                          if (ok) setClosingStop(null)
                        }}
                      />
                    )}
                  </div>
                  {editable && (
                    <div className="stop-actions">
                      {s.status === 'A_FAIRE' && closingStop !== s.id && (
                        <button
                          className="btn btn-outline btn-sm"
                          onClick={() => setClosingStop(s.id)}
                        >
                          Clôturer
                        </button>
                      )}
                      <button
                        className="btn btn-outline btn-sm"
                        onClick={() => move(i, -1)}
                        disabled={busy || i === 0}
                        aria-label="Monter"
                      >
                        ↑
                      </button>
                      <button
                        className="btn btn-outline btn-sm"
                        onClick={() => move(i, 1)}
                        disabled={busy || i === tour.stops.length - 1}
                        aria-label="Descendre"
                      >
                        ↓
                      </button>
                      {s.status === 'A_FAIRE' && (
                        <button
                          className="btn btn-danger btn-sm"
                          onClick={() => act(() => api.delete(`/tours/${tour.id}/stops/${s.id}`))}
                          disabled={busy}
                          aria-label="Retirer"
                        >
                          ✕
                        </button>
                      )}
                    </div>
                  )}
                </li>
              )
            })}
          </ol>
        )}
      </div>

      {editable && (
        <div className="card">
          <div className="card-title">
            <h3>Ajouter un arrêt</h3>
            <Link to="/sites" className="link">
              Gérer les sites →
            </Link>
          </div>
          <AddStopForm
            sites={sites}
            onAdd={(payload) =>
              act(() => api.post(`/tours/${tour.id}/stops`, payload), 'Arrêt ajouté')
            }
          />
        </div>
      )}
    </div>
  )
}
