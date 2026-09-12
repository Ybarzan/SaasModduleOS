import { useCallback, useEffect, useState } from 'react'
import api from '../services/api'

const STATE_BADGE = {
  ACTIVE: 'badge-green',
  PAUSE: 'badge-orange',
  IDLE: 'badge-gray'
}

const STATE_LABEL = {
  ACTIVE: 'En service',
  PAUSE: 'En pause',
  IDLE: 'Hors service'
}

const EVENT_LABEL = {
  DEBUT: 'Début de service',
  PAUSE_DEBUT: 'Début de pause',
  PAUSE_FIN: 'Reprise',
  FIN: 'Fin de service'
}

const fmtDuration = (totalSeconds) => {
  const s = Math.max(0, Math.round(totalSeconds || 0))
  const h = String(Math.floor(s / 3600)).padStart(2, '0')
  const m = String(Math.floor((s % 3600) / 60)).padStart(2, '0')
  return `${h}h${m}`
}

const fmtTime = (iso) => (iso ? new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }) : '—')

export default function Pointage() {
  const [statuses, setStatuses] = useState([])
  const [events, setEvents] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const load = useCallback(() => {
    Promise.all([api.get('/pointage/admin/status'), api.get('/pointage/admin/today')])
      .then(([s, e]) => {
        setStatuses(s.data)
        setEvents(e.data)
        setError('')
      })
      .catch(() => setError('Impossible de charger le pointage'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    load()
    const id = setInterval(load, 20000)
    return () => clearInterval(id)
  }, [load])

  const activeCount = statuses.filter((s) => s.state !== 'IDLE').length

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Pointage</h2>
          <p>Suivi en direct du service de vos chauffeurs (portail /pointage)</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="card desktop-table">
        <div className="card-title">
          <h3>En direct</h3>
          <span className="muted">{activeCount} chauffeur(s) en service ou en pause</span>
        </div>
        {loading ? (
          <p className="muted table-empty">Chargement…</p>
        ) : statuses.length === 0 ? (
          <p className="muted table-empty">Aucun chauffeur actif</p>
        ) : (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Chauffeur</th>
                  <th>Statut</th>
                  <th>Début de service</th>
                  <th>Conduite continue</th>
                  <th>Pause en cours</th>
                </tr>
              </thead>
              <tbody>
                {statuses.map((s, i) => (
                  <tr key={i}>
                    <td><strong>{s.driverName}</strong></td>
                    <td><span className={`badge ${STATE_BADGE[s.state]}`}>{STATE_LABEL[s.state]}</span></td>
                    <td>{fmtTime(s.serviceStartedAt)}</td>
                    <td>{s.state === 'IDLE' ? '—' : fmtDuration(s.continuousDrivingSeconds)}</td>
                    <td>{s.state === 'PAUSE' ? fmtDuration(s.pauseSeconds) : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {!loading && (
        <div className="record-cards">
          {statuses.length === 0 ? (
            <p className="muted table-empty">Aucun chauffeur actif</p>
          ) : (
            statuses.map((s, i) => (
              <div key={i} className="record-card">
                <div className="record-card-title">
                  <strong>{s.driverName}</strong>
                  <span className={`badge ${STATE_BADGE[s.state]}`}>{STATE_LABEL[s.state]}</span>
                </div>
                <div className="record-card-row">
                  <span>Début de service</span>
                  <span>{fmtTime(s.serviceStartedAt)}</span>
                </div>
                <div className="record-card-row">
                  <span>Conduite continue</span>
                  <span>{s.state === 'IDLE' ? '—' : fmtDuration(s.continuousDrivingSeconds)}</span>
                </div>
                <div className="record-card-row">
                  <span>Pause en cours</span>
                  <span>{s.state === 'PAUSE' ? fmtDuration(s.pauseSeconds) : '—'}</span>
                </div>
              </div>
            ))
          )}
        </div>
      )}

      <div className="card desktop-table">
        <div className="card-title">
          <h3>Journal du jour</h3>
          <span className="muted">{events.length} événement(s)</span>
        </div>
        {loading ? (
          <p className="muted table-empty">Chargement…</p>
        ) : events.length === 0 ? (
          <p className="muted table-empty">Aucun événement aujourd'hui</p>
        ) : (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Heure</th>
                  <th>Chauffeur</th>
                  <th>Événement</th>
                </tr>
              </thead>
              <tbody>
                {events.map((e, i) => (
                  <tr key={i}>
                    <td>{fmtTime(e.occurredAt)}</td>
                    <td>{e.driverName}</td>
                    <td>{EVENT_LABEL[e.type] || e.type}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {!loading && (
        <div className="record-cards">
          {events.length === 0 ? (
            <p className="muted table-empty">Aucun événement aujourd'hui</p>
          ) : (
            events.map((e, i) => (
              <div key={i} className="record-card">
                <div className="record-card-title">
                  <strong>{e.driverName}</strong>
                  <span className="muted">{fmtTime(e.occurredAt)}</span>
                </div>
                <div className="record-card-row">
                  <span>Événement</span>
                  <span>{EVENT_LABEL[e.type] || e.type}</span>
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  )
}
