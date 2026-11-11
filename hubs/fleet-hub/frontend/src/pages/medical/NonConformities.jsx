import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../../services/api'
import { NC_SEVERITY, NC_STATUS, NC_TYPES, dateTime } from '../../medical'
import { shiftDate, todayIso } from '../../tours'

const EMPTY = { tourId: '', type: 'AUTRE', severity: 'MINEURE', description: '' }

function DeclareForm({ onDone }) {
  const [form, setForm] = useState(EMPTY)
  const [tours, setTours] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  useEffect(() => {
    api
      .get('/tours', { params: { from: shiftDate(todayIso(), -13), to: todayIso() } })
      .then((res) => setTours(res.data.slice().reverse()))
      .catch(() => setTours([]))
  }, [])

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      await api.post('/medical/non-conformities', {
        tourId: form.tourId ? Number(form.tourId) : null,
        type: form.type,
        severity: form.severity,
        description: form.description.trim()
      })
      setForm(EMPTY)
      onDone()
    } catch (err) {
      setError(err.response?.data?.message || 'Déclaration impossible')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card" onSubmit={submit}>
      <div className="card-title">
        <h3>Déclarer une non-conformité</h3>
      </div>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="data-grid">
        <div className="form-field">
          <label htmlFor="nc-tour">Tournée concernée</label>
          <select id="nc-tour" value={form.tourId} onChange={set('tourId')}>
            <option value="">Aucune (hors tournée)</option>
            {tours.map((t) => (
              <option key={t.id} value={t.id}>
                {t.date} · {t.name}
                {t.driverName ? ` · ${t.driverName}` : ''}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="nc-type">Type</label>
          <select id="nc-type" value={form.type} onChange={set('type')}>
            {Object.entries(NC_TYPES).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="nc-severity">Gravité</label>
          <select id="nc-severity" value={form.severity} onChange={set('severity')}>
            {Object.entries(NC_SEVERITY).map(([k, v]) => (
              <option key={k} value={k}>
                {v.label}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field form-field-wide">
          <label htmlFor="nc-description">Description *</label>
          <textarea
            id="nc-description"
            rows={3}
            value={form.description}
            onChange={set('description')}
            required
            minLength={5}
            maxLength={1000}
            placeholder="Ex. sachet de prélèvements remis ouvert par la pharmacie, emballage triple non conforme"
          />
        </div>
      </div>
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? 'Envoi…' : 'Déclarer'}
        </button>
      </div>
    </form>
  )
}

function NcCard({ nc, onUpdated }) {
  const [action, setAction] = useState(nc.correctiveAction || '')
  const [open, setOpen] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const sev = NC_SEVERITY[nc.severity] || { label: nc.severity, badge: 'badge-gray' }
  const st = NC_STATUS[nc.status] || { label: nc.status, badge: 'badge-gray' }
  const closed = nc.status === 'CLOTUREE'

  const update = async (status) => {
    setError('')
    setBusy(true)
    try {
      await api.put(`/medical/non-conformities/${nc.id}`, {
        status,
        correctiveAction: action.trim() || null
      })
      setOpen(false)
      onUpdated()
    } catch (err) {
      setError(err.response?.data?.message || 'Mise à jour impossible')
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className={`nc-card sev-${nc.severity.toLowerCase()}` + (closed ? ' closed' : '')}>
      <div className="nc-head">
        <strong>
          #{nc.id} · {NC_TYPES[nc.type] || nc.type}
        </strong>
        <span className={`badge ${sev.badge}`}>{sev.label}</span>
        <span className={`badge ${st.badge}`}>{st.label}</span>
        {nc.automatic && <span className="badge badge-blue">Détection auto</span>}
      </div>
      <p className="nc-desc">{nc.description}</p>
      <p className="muted nc-meta">
        {nc.tourId ? (
          <Link to={`/tours/${nc.tourId}`}>
            {nc.tourName} ({nc.tourDate})
          </Link>
        ) : (
          'Hors tournée'
        )}
        {nc.siteName ? ` · ${nc.siteName}` : ''} · détectée le {dateTime(nc.detectedAt)} par {nc.detectedBy}
      </p>
      {nc.correctiveAction && (
        <p className="nc-action">
          <b>Action corrective :</b> {nc.correctiveAction}
          {closed && (
            <span className="muted">
              {' '}
              — clôturée le {dateTime(nc.closedAt)} par {nc.closedBy}
            </span>
          )}
        </p>
      )}
      {error && <div className="alert alert-error">{error}</div>}
      {!closed &&
        (open ? (
          <div className="nc-treat">
            <label htmlFor={`nc-action-${nc.id}`}>Action corrective</label>
            <textarea
              id={`nc-action-${nc.id}`}
              rows={2}
              value={action}
              onChange={(e) => setAction(e.target.value)}
              maxLength={1000}
              placeholder="Ce qui a été fait : laboratoire prévenu, produits retrouvés, glacière remplacée…"
            />
            <div className="form-actions">
              <button type="button" className="btn btn-outline" onClick={() => setOpen(false)}>
                Annuler
              </button>
              {nc.status === 'OUVERTE' && (
                <button
                  type="button"
                  className="btn btn-outline"
                  onClick={() => update('EN_TRAITEMENT')}
                  disabled={busy}
                >
                  Prendre en charge
                </button>
              )}
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => update('CLOTUREE')}
                disabled={busy || action.trim().length < 5}
              >
                Clôturer
              </button>
            </div>
          </div>
        ) : (
          <button type="button" className="btn btn-outline btn-sm" onClick={() => setOpen(true)}>
            Traiter
          </button>
        ))}
    </li>
  )
}

/** Non-conformités du transport médical : à traiter, historique, déclaration. */
export default function NonConformities() {
  const [view, setView] = useState('OUVERTES')
  const [items, setItems] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    setError('')
    const params =
      view === 'OUVERTES'
        ? { status: 'OUVERTES' }
        : { from: shiftDate(todayIso(), -89), to: todayIso() }
    api
      .get('/medical/non-conformities', { params })
      .then((res) => setItems(res.data))
      .catch(() => setError('Chargement impossible'))
  }, [view])

  useEffect(() => {
    load()
  }, [load])

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Non-conformités</h2>
          <p>
            Écarts de quantité, ruptures du froid, délais dépassés : détectés automatiquement à la
            clôture des passages, ou déclarés ici. La clôture exige une action corrective.
          </p>
        </div>
        <div className="header-actions">
          <button className="btn btn-primary" onClick={() => setShowForm((v) => !v)}>
            {showForm ? 'Fermer' : '+ Déclarer'}
          </button>
        </div>
      </div>

      {showForm && (
        <DeclareForm
          onDone={() => {
            setShowForm(false)
            setView('OUVERTES')
            load()
          }}
        />
      )}

      <div className="segmented" role="radiogroup" aria-label="Affichage">
        <button
          type="button"
          role="radio"
          aria-checked={view === 'OUVERTES'}
          className={view === 'OUVERTES' ? 'active' : ''}
          onClick={() => setView('OUVERTES')}
        >
          À traiter
        </button>
        <button
          type="button"
          role="radio"
          aria-checked={view === 'TOUTES'}
          className={view === 'TOUTES' ? 'active' : ''}
          onClick={() => setView('TOUTES')}
        >
          Historique 90 jours
        </button>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {items === null ? (
        <div className="loading-spinner">Chargement…</div>
      ) : items.length === 0 ? (
        <div className="card">
          <p className="muted table-empty">
            {view === 'OUVERTES' ? '✓ Aucune non-conformité à traiter.' : 'Aucune non-conformité sur 90 jours.'}
          </p>
        </div>
      ) : (
        <ul className="nc-list">
          {items.map((nc) => (
            <NcCard key={nc.id} nc={nc} onUpdated={load} />
          ))}
        </ul>
      )}
    </div>
  )
}
