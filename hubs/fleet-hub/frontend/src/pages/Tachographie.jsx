import { useEffect, useState } from 'react'
import api from '../services/api'

const RULES = [
  { title: 'Conduite journalière', value: '9 h / 10 h', desc: '9 h max par jour, prolongeable à 10 h au plus 2 jours par semaine.' },
  { title: 'Conduite continue', value: '4 h 30', desc: '4 h 30 de conduite max, puis pause obligatoire de 45 min.' },
  { title: 'Repos quotidien', value: '11 h', desc: '11 h de repos minimum entre deux journées (9 h réduit, 3 fois max par semaine).' },
  { title: 'Repos hebdomadaire', value: '45 h', desc: '45 h de repos hebdomadaire minimum après 6 journées de travail.' },
  { title: 'Cumuls glissants', value: '56 h / 90 h', desc: '56 h de conduite max par semaine et 90 h sur deux semaines consécutives.' }
]

const iso = (d) => d.toISOString().slice(0, 10)

const rateTone = (rate) => (rate >= 90 ? 'green' : rate >= 75 ? 'orange' : 'red')

export default function Tachographie() {
  const today = new Date()
  const [from, setFrom] = useState(iso(new Date(today.getFullYear(), today.getMonth(), today.getDate() - 29)))
  const [to, setTo] = useState(iso(today))
  const [drivers, setDrivers] = useState([])
  const [selectedDriver, setSelectedDriver] = useState('')
  const [summary, setSummary] = useState([])
  const [journal, setJournal] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    api
      .get('/drivers')
      .then((res) => setDrivers(res.data.map((d) => ({ id: d.id, label: `${d.firstName} ${d.lastName}` }))))
      .catch((err) => console.error('Failed to load drivers:', err))
  }, [])

  useEffect(() => {
    api
      .get('/tachograph-days/summary', { params: { from, to } })
      .then((res) => setSummary(res.data))
      .catch(() => setError('Impossible de charger la synthèse tachygraphe'))
  }, [from, to])

  useEffect(() => {
    api
      .get('/tachograph-days', { params: { driverId: selectedDriver || undefined, from, to } })
      .then((res) => setJournal(res.data))
      .catch(() => setError('Impossible de charger le journal de conduite'))
  }, [from, to, selectedDriver])

  const totals = summary.reduce(
    (acc, s) => ({
      days: acc.days + s.days,
      compliant: acc.compliant + s.compliantDays,
      nonCompliant: acc.nonCompliant + s.nonCompliantDays,
      week7: acc.week7 + s.totalDrivingLast7d,
      week14: acc.week14 + s.totalDrivingLast14d
    }),
    { days: 0, compliant: 0, nonCompliant: 0, week7: 0, week14: 0 }
  )
  const rate = totals.days > 0 ? Math.round((totals.compliant / totals.days) * 100) : 0

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Tachographie</h2>
          <p>Conformité des temps de conduite et de repos — règlement CE n° 561/2006</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="card">
        <div className="card-title">
          <h3>Règles 561/2006 appliquées</h3>
          <span className="muted">évaluées sur les relevés journaliers, fenêtres glissantes 7 et 14 jours</span>
        </div>
        <div className="tacho-rules">
          {RULES.map((r) => (
            <div className="tacho-rule" key={r.title}>
              <div className="tacho-rule-value">{r.value}</div>
              <div className="tacho-rule-title">{r.title}</div>
              <div className="tacho-rule-desc">{r.desc}</div>
            </div>
          ))}
        </div>
      </div>

      <div className="north-star-grid">
        <div className={`stat-card ${rateTone(rate)}`}>
          <div className="stat-icon">⏱</div>
          <div>
            <div className="stat-label">Conformité période</div>
            <div className="stat-value">
              {rate}%<span className="stat-unit">de {totals.days} jour(s)</span>
            </div>
            <div className="stat-sub">{totals.compliant} jours conformes</div>
          </div>
        </div>
        <div className="stat-card red">
          <div className="stat-icon">⚠</div>
          <div>
            <div className="stat-label">Non-conformités</div>
            <div className="stat-value">
              {totals.nonCompliant}<span className="stat-unit">jour(s)</span>
            </div>
            <div className="stat-sub">sur la période sélectionnée</div>
          </div>
        </div>
        <div className="stat-card green">
          <div className="stat-icon">🚚</div>
          <div>
            <div className="stat-label">Conduite 7 j</div>
            <div className="stat-value">
              {totals.week7.toFixed(1)}<span className="stat-unit">h</span>
            </div>
            <div className="stat-sub">cumul flotte (max 56 h/chauffeur)</div>
          </div>
        </div>
        <div className="stat-card blue">
          <div className="stat-icon">📅</div>
          <div>
            <div className="stat-label">Conduite 14 j</div>
            <div className="stat-value">
              {totals.week14.toFixed(1)}<span className="stat-unit">h</span>
            </div>
            <div className="stat-sub">cumul flotte (max 90 h/chauffeur)</div>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-title">
          <h3>Conformité par chauffeur</h3>
          <span className="muted">{summary.length} chauffeur(s)</span>
        </div>
        <div className="table-scroll">
          <table className="table table-hover">
            <thead>
              <tr>
                <th>Chauffeur</th>
                <th>Jours</th>
                <th>Conformes</th>
                <th>Non-conformes</th>
                <th>Taux</th>
                <th>Conduite 7 j</th>
                <th>Conduite 14 j</th>
                <th>Dernière non-conformité</th>
              </tr>
            </thead>
            <tbody>
              {summary.length === 0 && (
                <tr>
                  <td colSpan="8" className="table-empty">Aucune donnée tachygraphe sur la période</td>
                </tr>
              )}
              {summary.map((s) => (
                <tr key={s.driverId}>
                  <td className="cell-strong">{s.driverName}</td>
                  <td>{s.days}</td>
                  <td>{s.compliantDays}</td>
                  <td>{s.nonCompliantDays}</td>
                  <td>
                    <span className={`badge badge-${rateTone(s.complianceRate)}`}>{Math.round(s.complianceRate)}%</span>
                  </td>
                  <td>{s.totalDrivingLast7d.toFixed(1)} h</td>
                  <td>{s.totalDrivingLast14d.toFixed(1)} h</td>
                  <td>{s.lastNonCompliantDate ? new Date(s.lastNonCompliantDate).toLocaleDateString('fr-FR') : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="card">
        <div className="card-title">
          <h3>Journal de conduite</h3>
        </div>
        <div className="filter-bar">
          <div className="form-field">
            <label htmlFor="tacho-driver">Chauffeur</label>
            <select id="tacho-driver" value={selectedDriver} onChange={(e) => setSelectedDriver(e.target.value)}>
              <option value="">Tous</option>
              {drivers.map((d) => (
                <option key={d.id} value={d.id}>{d.label}</option>
              ))}
            </select>
          </div>
          <div className="form-field">
            <label htmlFor="tacho-from">Du</label>
            <input id="tacho-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div className="form-field">
            <label htmlFor="tacho-to">Au</label>
            <input id="tacho-to" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </div>
        </div>
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Chauffeur</th>
                <th>Conduite (h)</th>
                <th>Travail (h)</th>
                <th>Repos (min)</th>
                <th>Conformité</th>
                <th>Motifs</th>
              </tr>
            </thead>
            <tbody>
              {journal.length === 0 && (
                <tr>
                  <td colSpan="7" className="table-empty">Aucun jour de tachygraphe sur la période</td>
                </tr>
              )}
              {journal.map((d) => (
                <tr key={d.id}>
                  <td className="cell-strong">{new Date(d.date).toLocaleDateString('fr-FR')}</td>
                  <td>{d.driverName}</td>
                  <td>{d.drivingHours}</td>
                  <td>{d.workHours || '—'}</td>
                  <td>{d.restMinutes || '—'}</td>
                  <td>
                    {d.compliant ? (
                      <span className="badge badge-green">Conforme</span>
                    ) : (
                      <span className="badge badge-red">Non conforme</span>
                    )}
                  </td>
                  <td>
                    {d.reasons?.length ? (
                      <ul className="tacho-reasons">
                        {d.reasons.map((r) => (
                          <li key={r}>{r}</li>
                        ))}
                      </ul>
                    ) : (
                      <span className="muted">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
