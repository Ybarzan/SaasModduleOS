import { useEffect, useMemo, useState } from 'react'
import api from '../services/api'
import { SITE_KINDS, siteKindLabel } from '../tours'

const EMPTY = {
  name: '',
  kind: 'CLIENT',
  reference: '',
  address: '',
  postalCode: '',
  city: '',
  latitude: '',
  longitude: '',
  contactName: '',
  contactPhone: '',
  openingFrom: '',
  openingTo: '',
  serviceMinutes: '',
  notes: '',
  active: true
}

const toForm = (s) => {
  const f = { ...EMPTY }
  for (const k of Object.keys(EMPTY)) {
    const v = s[k]
    if (k === 'active') f[k] = !!v
    else if (k === 'openingFrom' || k === 'openingTo') f[k] = v ? String(v).slice(0, 5) : ''
    else f[k] = v == null ? '' : String(v)
  }
  return f
}

const toPayload = (f) => ({
  ...f,
  latitude: f.latitude === '' ? null : Number(f.latitude),
  longitude: f.longitude === '' ? null : Number(f.longitude),
  serviceMinutes: f.serviceMinutes === '' ? null : Number(f.serviceMinutes),
  openingFrom: f.openingFrom || null,
  openingTo: f.openingTo || null
})

function GeocodeSearch({ onPick }) {
  const [q, setQ] = useState('')
  const [results, setResults] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const search = async () => {
    setError('')
    setBusy(true)
    try {
      const res = await api.get('/sites/geocode', { params: { q } })
      setResults(res.data)
      if (res.data.length === 0) setError('Aucune adresse trouvée')
    } catch (err) {
      setError(err.response?.data?.message || 'Recherche impossible')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="geocode">
      <div className="geocode-bar">
        <input
          type="search"
          placeholder="Rechercher une adresse (ex. 12 rue de la République Lyon)"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault()
              search()
            }
          }}
        />
        <button
          type="button"
          className="btn btn-outline"
          onClick={search}
          disabled={busy || q.trim().length < 3}
        >
          {busy ? '…' : 'Localiser'}
        </button>
      </div>
      {error && <p className="muted">{error}</p>}
      {results.length > 0 && (
        <ul className="geocode-results">
          {results.map((r) => (
            <li key={`${r.latitude},${r.longitude}`}>
              <button
                type="button"
                onClick={() => {
                  onPick(r)
                  setResults([])
                  setQ('')
                }}
              >
                {r.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default function Sites() {
  const [sites, setSites] = useState([])
  const [form, setForm] = useState(EMPTY)
  const [editingId, setEditingId] = useState(null)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [search, setSearch] = useState('')
  const [kindFilter, setKindFilter] = useState('')

  const load = () =>
    api
      .get('/sites')
      .then((res) => setSites(res.data))
      .catch(() => setError('Impossible de charger les sites'))

  useEffect(() => {
    load()
  }, [])

  const set = (k) => (e) =>
    setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  const reset = () => {
    setForm(EMPTY)
    setEditingId(null)
  }

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setSuccess('')
    try {
      if (editingId) await api.put(`/sites/${editingId}`, toPayload(form))
      else await api.post('/sites', toPayload(form))
      setSuccess(editingId ? 'Site mis à jour' : 'Site ajouté')
      reset()
      load()
    } catch (err) {
      setError(err.response?.data?.message || 'Enregistrement impossible')
    }
  }

  const remove = async (s) => {
    if (!window.confirm(`Supprimer « ${s.name} » ?`)) return
    setError('')
    try {
      await api.delete(`/sites/${s.id}`)
      load()
    } catch (err) {
      setError(err.response?.data?.message || 'Suppression impossible')
    }
  }

  const filtered = useMemo(() => {
    const q = search.toLowerCase()
    return sites.filter(
      (s) =>
        (!kindFilter || s.kind === kindFilter) &&
        `${s.name} ${s.city || ''} ${s.reference || ''} ${s.address || ''}`
          .toLowerCase()
          .includes(q)
    )
  }, [sites, search, kindFilter])

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Sites</h2>
          <p>Clients, pharmacies, laboratoires et dépôts desservis par vos tournées</p>
        </div>
        <div className="header-actions">
          <input
            type="search"
            className="search-input"
            placeholder="Rechercher un site…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <select value={kindFilter} onChange={(e) => setKindFilter(e.target.value)}>
            <option value="">Tous les types</option>
            {SITE_KINDS.map((k) => (
              <option key={k.value} value={k.value}>
                {k.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {success && <div className="alert">{success}</div>}

      <div className="card">
        <div className="card-title">
          <h3>{editingId ? 'Modifier le site' : 'Nouveau site'}</h3>
          {editingId && (
            <button type="button" className="btn btn-outline btn-sm" onClick={reset}>
              Annuler
            </button>
          )}
        </div>
        <GeocodeSearch
          onPick={(r) =>
            setForm((f) => ({
              ...f,
              address: r.address || f.address,
              postalCode: r.postalCode || f.postalCode,
              city: r.city || f.city,
              latitude: String(r.latitude),
              longitude: String(r.longitude)
            }))
          }
        />
        <form onSubmit={submit}>
          <div className="data-grid">
            <div className="form-field">
              <label>Nom *</label>
              <input value={form.name} onChange={set('name')} required />
            </div>
            <div className="form-field">
              <label>Type *</label>
              <select value={form.kind} onChange={set('kind')}>
                {SITE_KINDS.map((k) => (
                  <option key={k.value} value={k.value}>
                    {k.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-field">
              <label>Référence (code client, FINESS…)</label>
              <input value={form.reference} onChange={set('reference')} />
            </div>
            <div className="form-field">
              <label>Adresse</label>
              <input value={form.address} onChange={set('address')} />
            </div>
            <div className="form-field">
              <label>Code postal</label>
              <input value={form.postalCode} onChange={set('postalCode')} />
            </div>
            <div className="form-field">
              <label>Ville</label>
              <input value={form.city} onChange={set('city')} />
            </div>
            <div className="form-field">
              <label>Latitude</label>
              <input type="number" step="any" value={form.latitude} onChange={set('latitude')} />
            </div>
            <div className="form-field">
              <label>Longitude</label>
              <input type="number" step="any" value={form.longitude} onChange={set('longitude')} />
            </div>
            <div className="form-field">
              <label>Contact</label>
              <input value={form.contactName} onChange={set('contactName')} />
            </div>
            <div className="form-field">
              <label>Téléphone</label>
              <input value={form.contactPhone} onChange={set('contactPhone')} />
            </div>
            <div className="form-field">
              <label>Ouverture</label>
              <input type="time" value={form.openingFrom} onChange={set('openingFrom')} />
            </div>
            <div className="form-field">
              <label>Fermeture</label>
              <input type="time" value={form.openingTo} onChange={set('openingTo')} />
            </div>
            <div className="form-field">
              <label>Durée de passage (min)</label>
              <input
                type="number"
                min="0"
                value={form.serviceMinutes}
                onChange={set('serviceMinutes')}
              />
            </div>
            <div className="form-field">
              <label>Consignes d'accès</label>
              <input value={form.notes} onChange={set('notes')} />
            </div>
            <label className="form-checkbox-row">
              <input type="checkbox" checked={form.active} onChange={set('active')} /> Actif
            </label>
          </div>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary">
              {editingId ? 'Enregistrer' : 'Ajouter le site'}
            </button>
          </div>
        </form>
      </div>

      <div className="card">
        <div className="card-title">
          <h3>Sites</h3>
          <span className="muted">{filtered.length} site(s)</span>
        </div>
        <div className="table-scroll">
          <table className="table table-hover">
            <thead>
              <tr>
                <th>Site</th>
                <th>Type</th>
                <th>Ville</th>
                <th>Créneau</th>
                <th>GPS</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((s) => (
                <tr key={s.id} className={s.active ? '' : 'row-muted'}>
                  <td className="cell-strong">
                    {s.name}
                    {s.reference && <span className="muted block">{s.reference}</span>}
                  </td>
                  <td>{siteKindLabel(s.kind)}</td>
                  <td>{[s.postalCode, s.city].filter(Boolean).join(' ') || '—'}</td>
                  <td>
                    {s.openingFrom || s.openingTo
                      ? `${(s.openingFrom || '').slice(0, 5)} – ${(s.openingTo || '').slice(0, 5)}`
                      : '—'}
                  </td>
                  <td>
                    {s.latitude != null ? (
                      <span className="badge badge-green">Localisé</span>
                    ) : (
                      <span className="badge badge-red">À localiser</span>
                    )}
                  </td>
                  <td>
                    <div className="row-actions">
                      <button
                        className="btn btn-outline btn-sm"
                        onClick={() => {
                          setForm(toForm(s))
                          setEditingId(s.id)
                          window.scrollTo({ top: 0, behavior: 'smooth' })
                        }}
                      >
                        Éditer
                      </button>
                      <button className="btn btn-danger btn-sm" onClick={() => remove(s)}>
                        Supprimer
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan="6" className="table-empty">
                    Aucun site. Commencez par votre dépôt puis vos clients.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
