import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../../services/api'
import { downloadFile, shiftDate, todayIso } from '../../tours'
import { CUSTODY_EVENTS, dateTime, describeProducts, parseProducts } from '../../medical'

/** Journal de garde du transport médical : consultation, vérification d'intégrité, export. */
export default function Traceability() {
  const [from, setFrom] = useState(shiftDate(todayIso(), -6))
  const [to, setTo] = useState(todayIso())
  const [type, setType] = useState('')
  const [query, setQuery] = useState('')
  const [events, setEvents] = useState(null)
  const [integrity, setIntegrity] = useState(null)
  const [checking, setChecking] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    setError('')
    api
      .get('/medical/custody', { params: { from, to } })
      .then((res) => setEvents(res.data))
      .catch((err) => setError(err.response?.data?.message || 'Chargement du journal impossible'))
  }, [from, to])

  const verify = useCallback(() => {
    setChecking(true)
    api
      .get('/medical/custody/verify')
      .then((res) => setIntegrity(res.data))
      .catch(() => setIntegrity(null))
      .finally(() => setChecking(false))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    verify()
  }, [verify])

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase()
    return (events || []).filter(
      (e) =>
        (!type || e.type === type) &&
        (!q ||
          [e.tourName, e.siteName, e.actor, e.signedBy, e.details]
            .filter(Boolean)
            .some((v) => v.toLowerCase().includes(q)))
    )
  }, [events, type, query])

  const exportCsv = () =>
    downloadFile(api, '/medical/custody.csv', { from, to }, `journal-garde_${from}_${to}.csv`).catch(() =>
      setError('Export impossible')
    )

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Traçabilité</h2>
          <p>
            Journal de garde : chaque prise en charge, remise, correction et non-conformité,
            horodatée, localisée et chaînée par empreinte SHA-256
          </p>
        </div>
        <div className="header-actions">
          <button className="btn btn-outline" onClick={verify} disabled={checking}>
            {checking ? 'Vérification…' : '🔒 Vérifier l’intégrité'}
          </button>
          <button className="btn btn-primary" onClick={exportCsv}>
            Exporter (CSV)
          </button>
        </div>
      </div>

      {integrity && (
        <div className={'integrity-banner ' + (integrity.valid ? 'ok' : 'ko')} role="status">
          {integrity.valid ? (
            <>
              🔒 Chaîne intègre : {integrity.eventsChecked} événement(s) vérifié(s). Dernière empreinte{' '}
              <code title={integrity.lastHash}>{integrity.lastHash.slice(0, 16)}…</code>
            </>
          ) : (
            <>
              ⚠ Chaîne rompue à l'événement n° {integrity.firstInvalidSequence} ({integrity.eventsChecked}{' '}
              événement(s) valides avant) : une donnée a été modifiée ou supprimée hors de l'application.
              Conservez l'export et alertez votre responsable qualité.
            </>
          )}
        </div>
      )}

      {error && <div className="alert alert-error">{error}</div>}

      <div className="card">
        <div className="filters-row">
          <label>
            Du
            <input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label>
            Au
            <input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
          </label>
          <label>
            Événement
            <select value={type} onChange={(e) => setType(e.target.value)}>
              <option value="">Tous</option>
              {Object.entries(CUSTODY_EVENTS).map(([k, v]) => (
                <option key={k} value={k}>
                  {v.label}
                </option>
              ))}
            </select>
          </label>
          <label className="grow">
            Recherche
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Tournée, site, chauffeur, signataire…"
            />
          </label>
        </div>

        {events === null ? (
          <div className="loading-spinner">Chargement…</div>
        ) : shown.length === 0 ? (
          <p className="muted table-empty">Aucun événement sur la période.</p>
        ) : (
          <div className="table-scroll">
            <table className="table custody-table">
              <thead>
                <tr>
                  <th>N°</th>
                  <th>Horodatage</th>
                  <th>Événement</th>
                  <th>Tournée / site</th>
                  <th>Produits</th>
                  <th>T °C</th>
                  <th>Remis à</th>
                  <th>Acteur</th>
                  <th>Détails</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((e) => {
                  const meta = CUSTODY_EVENTS[e.type] || { label: e.type, icon: '•' }
                  return (
                    <tr key={e.id} className={'ev-' + e.type.toLowerCase()}>
                      <td className="mono" title={`Empreinte ${e.hash}`}>
                        {e.sequenceNo}
                      </td>
                      <td>
                        {dateTime(e.occurredAt)}
                        {e.recordedAt !== e.occurredAt && (
                          <small className="muted block" title="Heure d'enregistrement serveur">
                            enreg. {dateTime(e.recordedAt)}
                          </small>
                        )}
                      </td>
                      <td>
                        <span className="ev-badge">
                          <span aria-hidden="true">{meta.icon}</span> {meta.label}
                        </span>
                      </td>
                      <td>
                        {e.tourId ? <Link to={`/tours/${e.tourId}`}>{e.tourName}</Link> : e.tourName || '—'}
                        {e.siteName && <small className="muted block">{e.siteName}</small>}
                        {e.latitude != null && (
                          <a
                            className="muted block"
                            href={`https://www.openstreetmap.org/?mlat=${e.latitude}&mlon=${e.longitude}#map=17/${e.latitude}/${e.longitude}`}
                            target="_blank"
                            rel="noreferrer"
                          >
                            📍 position
                          </a>
                        )}
                      </td>
                      <td>{e.products ? describeProducts(parseProducts(e.products)) : '—'}</td>
                      <td>{e.temperatureCelsius ?? '—'}</td>
                      <td>{e.signedBy || '—'}</td>
                      <td>{e.actor}</td>
                      <td className="ev-details">{e.details || ''}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        {events && events.length >= 2000 && (
          <p className="muted">Affichage limité aux 2 000 derniers événements : réduisez la période ou exportez.</p>
        )}
      </div>
    </div>
  )
}
