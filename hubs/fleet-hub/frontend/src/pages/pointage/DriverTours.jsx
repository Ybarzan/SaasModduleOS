import { useCallback, useEffect, useState } from 'react'
import api from '../../services/api'
import StopCompletionForm from '../../components/StopCompletionForm'
import { STOP_STATUS, hhmm, siteKindLabel, stopTypeLabel } from '../../tours'

const mapsUrl = (s) =>
  `https://www.google.com/maps/dir/?api=1&destination=${s.latitude},${s.longitude}`
const wazeUrl = (s) => `https://waze.com/ul?ll=${s.latitude},${s.longitude}&navigate=yes`

function StopCard({ tour, stop, isNext, busy, onAction }) {
  const [closing, setClosing] = useState(false)
  const st = STOP_STATUS[stop.status] || { label: stop.status, badge: 'badge-gray' }
  const open = stop.status === 'A_FAIRE'
  const running = tour.status === 'EN_COURS' || tour.status === 'PLANIFIEE'

  return (
    <div className={'card drv-stop' + (isNext ? ' next' : '') + (open ? '' : ' closed')}>
      <div className="drv-stop-head">
        <span className="drv-seq">{stop.sequence}</span>
        <div className="drv-stop-title">
          <strong>{stop.siteName}</strong>
          <span className="muted block">
            {stopTypeLabel(stop.type)} · {siteKindLabel(stop.siteKind)}
          </span>
        </div>
        <span className={`badge ${st.badge}`}>{st.label}</span>
      </div>

      <div className="drv-stop-info">
        {(stop.address || stop.city) && (
          <div>{[stop.address, stop.city].filter(Boolean).join(', ')}</div>
        )}
        <div>
          Prévu <b>{hhmm(stop.plannedArrival)}</b>
          {(stop.windowStart || stop.windowEnd) &&
            ` · créneau ${hhmm(stop.windowStart)}–${hhmm(stop.windowEnd)}`}
        </div>
        {(stop.temperatureMinCelsius != null || stop.temperatureMaxCelsius != null) && (
          <div>
            🌡 Maintenir entre {stop.temperatureMinCelsius ?? '…'} et{' '}
            {stop.temperatureMaxCelsius ?? '…'} °C
          </div>
        )}
        {stop.notes && <div className="muted">{stop.notes}</div>}
        {stop.arrivedAt && open && (
          <div className="muted">
            Arrivé à{' '}
            {new Date(stop.arrivedAt).toLocaleTimeString('fr-FR', {
              hour: '2-digit',
              minute: '2-digit'
            })}
          </div>
        )}
        {!open && (
          <div className="muted">
            {stop.failureReason
              ? `Échec : ${stop.failureReason}`
              : [
                  stop.signedBy && `Remis à ${stop.signedBy}`,
                  stop.sampleCount != null && `${stop.sampleCount} échantillon(s)`,
                  stop.parcelCount != null && `${stop.parcelCount} colis`,
                  stop.temperatureCelsius != null && `${stop.temperatureCelsius} °C`
                ]
                  .filter(Boolean)
                  .join(' · ')}
          </div>
        )}
      </div>

      {open && running && !closing && (
        <div className="drv-actions">
          {stop.latitude != null && (
            <>
              <a className="btn btn-outline" href={mapsUrl(stop)} target="_blank" rel="noreferrer">
                🧭 Itinéraire
              </a>
              <a className="btn btn-outline" href={wazeUrl(stop)} target="_blank" rel="noreferrer">
                Waze
              </a>
            </>
          )}
          {stop.contactPhone && (
            <a className="btn btn-outline" href={`tel:${stop.contactPhone.replace(/\s/g, '')}`}>
              📞 Appeler
            </a>
          )}
          {!stop.arrivedAt && (
            <button
              className="btn btn-outline"
              disabled={busy}
              onClick={() => onAction('arrive', stop)}
            >
              📍 Je suis arrivé
            </button>
          )}
          <button className="btn btn-primary" disabled={busy} onClick={() => setClosing(true)}>
            ✓ Clôturer
          </button>
        </div>
      )}

      {closing && (
        <StopCompletionForm
          stop={stop}
          busy={busy}
          onCancel={() => setClosing(false)}
          onSubmit={async (payload) => {
            const ok = await onAction('complete', stop, payload)
            if (ok) setClosing(false)
          }}
        />
      )}
    </div>
  )
}

/** Tournées du jour du chauffeur connecté (portail CHAUFFEUR). */
export default function DriverTours() {
  const [tours, setTours] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(
    () =>
      api
        .get('/me/tours')
        .then((res) => setTours(res.data))
        .catch(() => setError('Impossible de charger vos tournées')),
    []
  )

  useEffect(() => {
    load()
    const id = setInterval(load, 60000)
    return () => clearInterval(id)
  }, [load])

  const replace = (updated) => setTours((ts) => ts.map((t) => (t.id === updated.id ? updated : t)))

  const onAction = async (action, stop, payload) => {
    setError('')
    setBusy(true)
    const tour = tours.find((t) => t.stops.some((s) => s.id === stop.id))
    try {
      const res =
        action === 'arrive'
          ? await api.post(`/me/tours/${tour.id}/stops/${stop.id}/arrive`)
          : await api.post(`/me/tours/${tour.id}/stops/${stop.id}/complete`, payload)
      replace(res.data)
      window.__fhHaptics?.medium()
      return true
    } catch (err) {
      setError(err.response?.data?.message || "L'action a échoué")
      return false
    } finally {
      setBusy(false)
    }
  }

  const startTour = async (tour) => {
    setError('')
    setBusy(true)
    try {
      replace((await api.post(`/me/tours/${tour.id}/start`)).data)
    } catch (err) {
      setError(err.response?.data?.message || "L'action a échoué")
    } finally {
      setBusy(false)
    }
  }

  if (tours === null) {
    return error ? (
      <div className="alert alert-error">{error}</div>
    ) : (
      <div className="ptg-center">
        <span className="spinner" />
      </div>
    )
  }

  if (tours.length === 0) {
    return (
      <div className="card">
        <div style={{ fontWeight: 700 }}>Aucune tournée aujourd'hui</div>
        <div className="muted" style={{ fontSize: 13, marginTop: 4 }}>
          Votre exploitant ne vous a pas encore affecté de tournée.
        </div>
      </div>
    )
  }

  return (
    <div className="drv-tours">
      {error && <div className="alert alert-error">{error}</div>}
      {tours.map((tour) => {
        const next = tour.stops.find((s) => s.status === 'A_FAIRE')
        const closed = tour.stopsDone + tour.stopsFailed
        return (
          <section key={tour.id}>
            <div className="card drv-tour-head">
              <div>
                <strong>{tour.name}</strong>
                <span className="muted block">
                  Départ {hhmm(tour.plannedStart)}
                  {tour.truckRegistration ? ` · ${tour.truckRegistration}` : ''}
                  {tour.plannedDistanceKm != null ? ` · ${tour.plannedDistanceKm} km` : ''}
                </span>
              </div>
              <div className="drv-progress-label">
                {closed}/{tour.stopsTotal}
              </div>
              <div className="tour-progress drv-progress">
                <span
                  style={{ width: `${tour.stopsTotal ? (closed / tour.stopsTotal) * 100 : 0}%` }}
                />
              </div>
              {tour.status === 'PLANIFIEE' && (
                <button
                  className="btn btn-primary btn-block"
                  disabled={busy}
                  onClick={() => startTour(tour)}
                >
                  ▶ Démarrer la tournée
                </button>
              )}
              {tour.status === 'TERMINEE' && <div className="drv-done">✓ Tournée terminée</div>}
            </div>
            {tour.stops.map((s) => (
              <StopCard
                key={s.id}
                tour={tour}
                stop={s}
                isNext={next?.id === s.id && tour.status !== 'TERMINEE'}
                busy={busy}
                onAction={onAction}
              />
            ))}
          </section>
        )
      })}
    </div>
  )
}
