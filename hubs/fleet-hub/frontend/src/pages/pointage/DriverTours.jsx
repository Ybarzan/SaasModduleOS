import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import api from '../../services/api'
import { hhmm, siteKindLabel, stopTypeLabel } from '../../tours'
import {
  cancel,
  enqueue,
  flush,
  localNow,
  pendingCount,
  pendingFor,
  subscribe
} from './driverQueue'
import { useGeofence, usePref, useWakeLock } from './driverHooks'
import { OkSheet, ProblemSheet, canValidateDirectly } from './QuickProof'

const CACHE_KEY = 'fh_driver_tours'

const navUrl = (s, app) =>
  app === 'waze'
    ? `https://waze.com/ul?ll=${s.latitude},${s.longitude}&navigate=yes`
    : `https://www.google.com/maps/dir/?api=1&destination=${s.latitude},${s.longitude}`

const haptic = (kind = 'light') => window.__fhHaptics?.[kind]?.()

function readCache() {
  try {
    return JSON.parse(localStorage.getItem(CACHE_KEY) || 'null')
  } catch {
    return null
  }
}

function writeCache(tours) {
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify(tours))
  } catch {
    /* cache indisponible */
  }
}

/** Arrêt affiché = état serveur + actions locales pas encore confirmées (hors ligne ou délai d'annulation). */
const withOverrides = (stop, overrides) =>
  overrides[stop.id] ? { ...stop, ...overrides[stop.id] } : stop

function windowLabel(s) {
  if (s.windowStart && s.windowEnd) return `${hhmm(s.windowStart)} – ${hhmm(s.windowEnd)}`
  if (s.windowEnd) return `avant ${hhmm(s.windowEnd)}`
  if (s.windowStart) return `après ${hhmm(s.windowStart)}`
  return null
}

function FocusCard({ stop, index, total }) {
  const win = windowLabel(stop)
  return (
    <article className="drv-focus" aria-label={`Prochain arrêt : ${stop.siteName}`}>
      <div className="drv-focus-top">
        <span className="drv-focus-seq">
          Arrêt {index} / {total}
        </span>
        <span className="drv-focus-type">{stopTypeLabel(stop.type)}</span>
      </div>
      <h2 className="drv-focus-name">{stop.siteName}</h2>
      {(stop.address || stop.city) && (
        <div className="drv-focus-address">
          {[stop.address, stop.city].filter(Boolean).join(', ')}
        </div>
      )}
      <div className="drv-focus-facts">
        <div>
          <span>Prévu</span>
          <b>{hhmm(stop.plannedArrival)}</b>
        </div>
        {win && (
          <div>
            <span>Créneau</span>
            <b>{win}</b>
          </div>
        )}
        {stop.expectedQuantity != null && (
          <div>
            <span>{stop.type === 'COLLECTE' ? 'À collecter' : 'À livrer'}</span>
            <b>{stop.expectedQuantity}</b>
          </div>
        )}
        {(stop.temperatureMinCelsius != null || stop.temperatureMaxCelsius != null) && (
          <div>
            <span>Froid</span>
            <b>
              {stop.temperatureMinCelsius ?? '…'}–{stop.temperatureMaxCelsius ?? '…'} °C
            </b>
          </div>
        )}
      </div>
      {stop.notes && <div className="drv-focus-notes">📝 {stop.notes}</div>}
      {stop.arrivedAt && (
        <div className="drv-focus-arrived">
          📍 Sur place depuis{' '}
          {new Date(stop.arrivedAt).toLocaleTimeString('fr-FR', {
            hour: '2-digit',
            minute: '2-digit'
          })}
        </div>
      )}
    </article>
  )
}

export default function DriverTours() {
  const [tours, setTours] = useState(() => readCache())
  const [overrides, setOverrides] = useState({})
  const [offline, setOffline] = useState(false)
  const [pending, setPending] = useState(pendingCount())
  const [focusId, setFocusId] = useState(null)
  const [tourId, setTourId] = useState(null)
  const [sheet, setSheet] = useState(null) // 'ok' | 'problem'
  const [toast, setToast] = useState(null)
  const [showList, setShowList] = useState(false)
  const [showSettings, setShowSettings] = useState(false)
  const [error, setError] = useState('')
  const [autoArrive, setAutoArrive] = usePref('fh_driver_auto_arrive', true)
  const [navApp, setNavApp] = usePref('fh_driver_nav_app', 'maps')
  const toastTimer = useRef(null)

  const load = useCallback(
    () =>
      api
        .get('/me/tours')
        .then((res) => {
          setTours(res.data)
          writeCache(res.data)
          setOffline(false)
        })
        .catch((err) => {
          if (!err.response) setOffline(true)
          else setError('Impossible de charger vos tournées')
        }),
    []
  )

  useEffect(() => {
    load()
    flush()
    const id = setInterval(load, 60000)
    return () => clearInterval(id)
  }, [load])

  // Réponses de la file d'envoi : l'état serveur remplace l'état local
  useEffect(
    () =>
      subscribe((ev) => {
        setPending(ev.pending)
        if (ev.type === 'offline') setOffline(true)
        if (ev.type === 'sent') {
          setOffline(false)
          if (ev.tour?.id) {
            setTours((ts) => {
              const next = (ts || []).map((t) => (t.id === ev.tour.id ? ev.tour : t))
              writeCache(next)
              return next
            })
          }
          if (pendingFor(ev.item.stopId).length === 0) {
            setOverrides((o) => {
              const next = { ...o }
              delete next[ev.item.stopId]
              return next
            })
          }
        }
        if (ev.type === 'rejected') {
          setError(ev.message)
          setOverrides((o) => {
            const next = { ...o }
            delete next[ev.item.stopId]
            return next
          })
          load()
        }
      }),
    [load]
  )

  const activeTour = useMemo(() => {
    if (!tours || tours.length === 0) return null
    return (
      tours.find((t) => t.id === tourId) ||
      tours.find((t) => t.status !== 'TERMINEE') ||
      tours[tours.length - 1]
    )
  }, [tours, tourId])

  const stops = useMemo(
    () => (activeTour ? activeTour.stops.map((s) => withOverrides(s, overrides)) : []),
    [activeTour, overrides]
  )
  const open = stops.filter((s) => s.status === 'A_FAIRE')
  const current = stops.find((s) => s.id === focusId && s.status === 'A_FAIRE') || open[0] || null
  const closedCount = stops.length - open.length
  const finished = activeTour && stops.length > 0 && open.length === 0

  useWakeLock(Boolean(activeTour) && !finished)

  const showToast = (text, undo) => {
    clearTimeout(toastTimer.current)
    setToast({ text, undo })
    toastTimer.current = setTimeout(() => setToast(null), 5000)
  }

  const markArrived = useCallback(
    (stop, auto = false) => {
      if (!activeTour || !stop || stop.arrivedAt) return
      setOverrides((o) => ({ ...o, [stop.id]: { ...o[stop.id], arrivedAt: localNow() } }))
      enqueue({
        url: `/me/tours/${activeTour.id}/stops/${stop.id}/arrive`,
        body: {},
        stopId: stop.id,
        kind: 'arrive',
        undoable: false
      })
      haptic(auto ? 'medium' : 'light')
      if (auto) showToast(`📍 Arrivée détectée — ${stop.siteName}`)
    },
    [activeTour]
  )

  const geo = useGeofence(
    current && !current.arrivedAt && !finished ? current : null,
    () => markArrived(current, true),
    { enabled: autoArrive }
  )

  const complete = (stop, payload) => {
    const previous = overrides[stop.id]
    const now = localNow()
    setOverrides((o) => ({
      ...o,
      [stop.id]: {
        ...o[stop.id],
        ...payload,
        status: payload.status,
        completedAt: now,
        arrivedAt: stop.arrivedAt || now
      }
    }))
    const id = enqueue({
      url: `/me/tours/${activeTour.id}/stops/${stop.id}/complete`,
      body: payload,
      stopId: stop.id,
      kind: 'complete'
    })
    setSheet(null)
    setFocusId(null)
    haptic(payload.status === 'FAIT' ? 'medium' : 'heavy')
    showToast(
      payload.status === 'FAIT'
        ? `✓ ${stop.siteName} validé`
        : `⚠ Échec enregistré — ${stop.siteName}`,
      () => {
        if (cancel(id)) {
          setOverrides((o) => {
            const next = { ...o }
            if (previous) next[stop.id] = previous
            else delete next[stop.id]
            return next
          })
          setFocusId(stop.id)
        }
        setToast(null)
      }
    )
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const onOk = () => {
    if (canValidateDirectly(current))
      complete(current, { status: 'FAIT', signedBy: current.suggestedSigner || null })
    else setSheet('ok')
  }

  if (tours === null) {
    if (error) return <div className="alert alert-error">{error}</div>
    if (offline) {
      return (
        <div className="card">
          <div style={{ fontWeight: 700 }}>📴 Pas de réseau</div>
          <div className="muted" style={{ fontSize: 14, marginTop: 4 }}>
            Ouvrez cet écran une première fois avec du réseau (au dépôt) : la tournée restera
            ensuite disponible hors ligne.
          </div>
        </div>
      )
    }
    return (
      <div className="ptg-center">
        <span className="spinner" />
      </div>
    )
  }

  if (!activeTour) {
    return (
      <div className="card">
        <div style={{ fontWeight: 700 }}>Aucune tournée aujourd'hui</div>
        <div className="muted" style={{ fontSize: 14, marginTop: 4 }}>
          Votre exploitant ne vous a pas encore affecté de tournée.
        </div>
      </div>
    )
  }

  return (
    <div className="drv">
      {tours.length > 1 && (
        <div className="drv-tour-switch" role="tablist">
          {tours.map((t) => (
            <button
              key={t.id}
              role="tab"
              aria-selected={t.id === activeTour.id}
              className={t.id === activeTour.id ? 'on' : ''}
              onClick={() => {
                setTourId(t.id)
                setFocusId(null)
              }}
            >
              {t.name}
            </button>
          ))}
        </div>
      )}

      <div className="drv-status">
        <div className="drv-status-row">
          <span>
            <b>
              {closedCount}/{stops.length}
            </b>{' '}
            arrêts
          </span>
          {(offline || pending > 0) && (
            <span className={'drv-sync' + (offline ? ' off' : '')}>
              {offline ? '📴 Hors ligne' : '⏳'}
              {pending > 0 ? ` · ${pending} à envoyer` : ''}
            </span>
          )}
          <button
            type="button"
            className="drv-gear"
            aria-label="Réglages"
            aria-expanded={showSettings}
            onClick={() => setShowSettings((v) => !v)}
          >
            ⚙
          </button>
        </div>
        <div className="drv-progress" aria-hidden="true">
          <span style={{ width: `${stops.length ? (closedCount / stops.length) * 100 : 0}%` }} />
        </div>
      </div>

      {showSettings && (
        <div className="card drv-settings">
          <label className="drv-toggle">
            <input
              type="checkbox"
              checked={autoArrive}
              onChange={(e) => setAutoArrive(e.target.checked)}
            />
            <span>
              Détecter mon arrivée automatiquement (GPS)
              {geo.error === 'refusee' && (
                <small className="text-red"> — localisation refusée par le téléphone</small>
              )}
            </span>
          </label>
          <div className="drv-nav-choice" role="radiogroup" aria-label="Application de navigation">
            {[
              ['maps', 'Google Maps'],
              ['waze', 'Waze']
            ].map(([v, l]) => (
              <button
                key={v}
                type="button"
                role="radio"
                aria-checked={navApp === v}
                className={navApp === v ? 'on' : ''}
                onClick={() => setNavApp(v)}
              >
                {l}
              </button>
            ))}
          </div>
        </div>
      )}

      {error && (
        <div className="alert alert-error" role="alert">
          {error}{' '}
          <button type="button" className="link-button" onClick={() => setError('')}>
            OK
          </button>
        </div>
      )}

      {finished ? (
        <div className="drv-finished">
          <div className="drv-finished-icon">✓</div>
          <h2>Tournée terminée</h2>
          <p>
            {stops.filter((s) => s.status === 'FAIT').length} réalisé(s) ·{' '}
            {stops.filter((s) => s.status === 'ECHEC').length} échec(s)
          </p>
          {pending > 0 && (
            <p className="muted">{pending} action(s) seront envoyées dès que le réseau revient.</p>
          )}
        </div>
      ) : (
        current && (
          <FocusCard stop={current} index={stops.indexOf(current) + 1} total={stops.length} />
        )
      )}

      {!finished && current && geo.distance != null && !current.arrivedAt && (
        <div className="drv-distance">
          À {geo.distance >= 1000 ? `${(geo.distance / 1000).toFixed(1)} km` : `${geo.distance} m`}
        </div>
      )}

      <button
        type="button"
        className="drv-list-toggle"
        aria-expanded={showList}
        onClick={() => setShowList((v) => !v)}
      >
        {showList ? 'Masquer la liste' : `Voir tous les arrêts (${stops.length})`}
      </button>

      {showList && (
        <ol className="drv-list">
          {stops.map((s) => (
            <li key={s.id}>
              <button
                type="button"
                className={
                  'drv-row ' +
                  s.status.toLowerCase() +
                  (current && s.id === current.id ? ' current' : '')
                }
                disabled={s.status !== 'A_FAIRE'}
                onClick={() => {
                  setFocusId(s.id)
                  setShowList(false)
                  window.scrollTo({ top: 0, behavior: 'smooth' })
                }}
              >
                <span className="drv-row-seq">
                  {s.status === 'FAIT' ? '✓' : s.status === 'ECHEC' ? '!' : s.sequence}
                </span>
                <span className="drv-row-main">
                  <b>{s.siteName}</b>
                  <small>
                    {siteKindLabel(s.siteKind)} · {hhmm(s.plannedArrival)}
                    {windowLabel(s) ? ` · ${windowLabel(s)}` : ''}
                  </small>
                </span>
              </button>
            </li>
          ))}
        </ol>
      )}

      {!finished && current && !sheet && (
        <div className="drv-actionbar">
          {!current.arrivedAt ? (
            <>
              {current.latitude != null ? (
                <a
                  className="drv-btn secondary"
                  href={navUrl(current, navApp)}
                  target="_blank"
                  rel="noreferrer"
                >
                  🧭 Naviguer
                </a>
              ) : current.contactPhone ? (
                <a
                  className="drv-btn secondary"
                  href={`tel:${current.contactPhone.replace(/\s/g, '')}`}
                >
                  📞 Appeler
                </a>
              ) : null}
              <button
                type="button"
                className="drv-btn primary"
                onClick={() => markArrived(current)}
              >
                📍 Je suis arrivé
              </button>
            </>
          ) : (
            <>
              <button type="button" className="drv-btn warn" onClick={() => setSheet('problem')}>
                ⚠ Problème
              </button>
              <button type="button" className="drv-btn ok" onClick={onOk}>
                ✓ Tout est OK
              </button>
            </>
          )}
          {current.arrivedAt && current.contactPhone && (
            <a
              className="drv-btn ghost"
              href={`tel:${current.contactPhone.replace(/\s/g, '')}`}
              aria-label="Appeler le site"
            >
              📞
            </a>
          )}
        </div>
      )}

      {sheet && <div className="qp-backdrop" onClick={() => setSheet(null)} />}
      {sheet === 'ok' && current && (
        <OkSheet
          stop={current}
          onSubmit={(p) => complete(current, p)}
          onClose={() => setSheet(null)}
        />
      )}
      {sheet === 'problem' && current && (
        <ProblemSheet
          stop={current}
          onSubmit={(p) => complete(current, p)}
          onClose={() => setSheet(null)}
        />
      )}

      {toast && (
        <div className="drv-toast" role="status">
          <span>{toast.text}</span>
          {toast.undo && (
            <button type="button" onClick={toast.undo}>
              Annuler
            </button>
          )}
        </div>
      )}
    </div>
  )
}
