import { useEffect, useMemo, useState } from 'react'
import api from '../services/api'

const TRANSPORT_MODES = [
  { value: 'SEA', label: 'Maritime' },
  { value: 'AIR', label: 'Aérien' },
  { value: 'ROAD', label: 'Routier' }
]

const modeLabel = (m) => TRANSPORT_MODES.find((t) => t.value === m)?.label || m

const EMPTY_FORM = {
  name: '',
  code: '',
  transportModes: '',
  country: '',
  contactName: '',
  contactEmail: '',
  contactPhone: '',
  logoUrl: ''
}

export default function Carriers() {
  const [carriers, setCarriers] = useState([])
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [selectedModes, setSelectedModes] = useState([])
  const [submitting, setSubmitting] = useState(false)
  const [busyId, setBusyId] = useState(null)

  const load = () =>
    api
      .get('/carriers')
      .then((res) => setCarriers(res.data))
      .catch(() => setError('Impossible de charger les transporteurs'))
      .finally(() => setLoading(false))

  useEffect(() => {
    load()
  }, [])

  const filtered = useMemo(() => {
    const q = search.toLowerCase()
    return carriers.filter((c) =>
      `${c.name} ${c.code} ${c.country || ''}`.toLowerCase().includes(q)
    )
  }, [carriers, search])

  const openCreate = () => {
    setEditing(null)
    setForm(EMPTY_FORM)
    setSelectedModes([])
    setShowForm(true)
  }

  const openEdit = (c) => {
    setEditing(c)
    const modes = c.transportModes.split(',').map((m) => m.trim())
    setSelectedModes(modes)
    setForm({
      name: c.name,
      code: c.code,
      transportModes: c.transportModes,
      country: c.country || '',
      contactName: c.contactName || '',
      contactEmail: c.contactEmail || '',
      contactPhone: c.contactPhone || '',
      logoUrl: c.logoUrl || ''
    })
    setShowForm(true)
  }

  const closeForm = () => {
    setShowForm(false)
    setEditing(null)
    setForm(EMPTY_FORM)
    setSelectedModes([])
  }

  const toggleMode = (mode) => {
    const next = selectedModes.includes(mode)
      ? selectedModes.filter((m) => m !== mode)
      : [...selectedModes, mode]
    setSelectedModes(next)
    setForm({ ...form, transportModes: next.join(',') })
  }

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setNotice('')
    if (!form.name.trim() || !form.code.trim()) {
      setError('Le nom et le code sont obligatoires')
      return
    }
    if (selectedModes.length === 0) {
      setError('Sélectionnez au moins un mode de transport')
      return
    }
    setSubmitting(true)
    try {
      if (editing) {
        await api.put(`/carriers/${editing.id}`, form)
        setNotice('Transporteur mis à jour')
      } else {
        await api.post('/carriers', form)
        setNotice('Transporteur créé avec succès')
      }
      closeForm()
      await load()
    } catch (err) {
      setError(err.response?.data?.message || "L'enregistrement a échoué")
    } finally {
      setSubmitting(false)
    }
  }

  const toggleActive = async (c) => {
    setBusyId(c.id)
    setError('')
    try {
      await api.patch(`/carriers/${c.id}/toggle`)
      await load()
    } catch {
      setError('Le changement de statut a échoué')
    } finally {
      setBusyId(null)
    }
  }

  const remove = async (c) => {
    if (!window.confirm(`Supprimer définitivement le transporteur ${c.name} ?`)) return
    setBusyId(c.id)
    setError('')
    try {
      await api.delete(`/carriers/${c.id}`)
      setNotice('Transporteur supprimé')
      await load()
    } catch (err) {
      setError(err.response?.data?.message || 'La suppression a échoué')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Transporteurs</h2>
          <p>Compagnies de transport partenaires</p>
        </div>
        <div className="header-actions">
          <input
            type="search"
            placeholder="Rechercher…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="search-input"
          />
          <button className="btn btn-primary" onClick={openCreate}>
            + Nouveau transporteur
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      {showForm && (
        <div className="card">
          <div className="card-title">
            <h3>{editing ? 'Modifier le transporteur' : 'Ajouter un transporteur'}</h3>
          </div>
          <form onSubmit={handleSubmit}>
            <div className="data-grid">
              <div className="form-field">
                <label>Nom *</label>
                <input
                  type="text"
                  value={form.name}
                  required
                  onChange={set('name')}
                  placeholder="DHL Express"
                />
              </div>
              <div className="form-field">
                <label>Code *</label>
                <input
                  type="text"
                  value={form.code}
                  required
                  onChange={set('code')}
                  placeholder="DHL"
                />
              </div>
              <div className="form-field">
                <label>Pays</label>
                <input
                  type="text"
                  value={form.country}
                  onChange={set('country')}
                  placeholder="France"
                />
              </div>
              <div className="form-field">
                <label>Contact</label>
                <input
                  type="text"
                  value={form.contactName}
                  onChange={set('contactName')}
                  placeholder="Nom du contact"
                />
              </div>
              <div className="form-field">
                <label>Email</label>
                <input
                  type="email"
                  value={form.contactEmail}
                  onChange={set('contactEmail')}
                  placeholder="contact@transporteur.com"
                />
              </div>
              <div className="form-field">
                <label>Téléphone</label>
                <input
                  type="tel"
                  value={form.contactPhone}
                  onChange={set('contactPhone')}
                  placeholder="+33 1 23 45 67 89"
                />
              </div>
              <div className="form-field">
                <label>Logo URL</label>
                <input
                  type="url"
                  value={form.logoUrl}
                  onChange={set('logoUrl')}
                  placeholder="https://…"
                />
              </div>
            </div>
            <div className="form-field" style={{ marginTop: 14 }}>
              <label>Modes de transport *</label>
              <div className="chip-row">
                {TRANSPORT_MODES.map((m) => (
                  <button
                    key={m.value}
                    type="button"
                    className={`chip ${selectedModes.includes(m.value) ? 'active' : ''}`}
                    onClick={() => toggleMode(m.value)}
                  >
                    {m.label}
                  </button>
                ))}
              </div>
            </div>
            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Enregistrement…' : editing ? 'Mettre à jour' : 'Créer'}
              </button>
              <button type="button" className="btn btn-outline" onClick={closeForm}>
                Annuler
              </button>
            </div>
          </form>
        </div>
      )}

      {loading && (
        <div className="loading-spinner" aria-label="Chargement des transporteurs…">
          Chargement…
        </div>
      )}

      <div className="card desktop-table">
        <div className="card-title">
          <h3>Liste des transporteurs</h3>
          <span className="muted">{filtered.length} transporteur(s)</span>
        </div>
        <div className="table-scroll">
          <table className="table table-hover">
            <thead>
              <tr>
                <th>Transporteur</th>
                <th>Modes</th>
                <th>Pays</th>
                <th>Contact</th>
                <th>Statut</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((c) => (
                <tr key={c.id}>
                  <td className="cell-strong">
                    <strong>{c.name}</strong>
                    <span className="muted block">{c.code}</span>
                  </td>
                  <td>
                    {c.transportModes
                      .split(',')
                      .map((m) => m.trim())
                      .map((m) => (
                        <span key={m} className="badge badge-blue" style={{ marginRight: 4 }}>
                          {modeLabel(m)}
                        </span>
                      ))}
                  </td>
                  <td>{c.country || '—'}</td>
                  <td className="muted">{c.contactEmail || c.contactName || '—'}</td>
                  <td>
                    <span className={`badge ${c.active ? 'badge-green' : 'badge-gray'}`}>
                      {c.active ? 'Actif' : 'Inactif'}
                    </span>
                  </td>
                  <td>
                    <div className="row-actions">
                      <button
                        className="btn btn-outline btn-sm"
                        disabled={busyId === c.id}
                        onClick={() => toggleActive(c)}
                      >
                        {c.active ? 'Désactiver' : 'Activer'}
                      </button>
                      <button className="btn btn-outline btn-sm" onClick={() => openEdit(c)}>
                        Modifier
                      </button>
                      <button
                        className="btn btn-danger btn-sm"
                        disabled={busyId === c.id}
                        onClick={() => remove(c)}
                      >
                        Supprimer
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && !error && (
                <tr>
                  <td colSpan={6} className="table-empty">
                    Aucun transporteur ne correspond à la recherche
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {!loading && (
        <div className="record-cards">
          {filtered.length === 0 ? (
            <p className="muted table-empty">Aucun transporteur</p>
          ) : (
            filtered.map((c) => (
              <div key={c.id} className="record-card">
                <div className="record-card-title">
                  <strong>
                    {c.name}
                    <span className="muted block">{c.code}</span>
                  </strong>
                  <span className={`badge ${c.active ? 'badge-green' : 'badge-gray'}`}>
                    {c.active ? 'Actif' : 'Inactif'}
                  </span>
                </div>
                <div className="record-card-row">
                  <span>Modes</span>
                  <span>
                    {c.transportModes
                      .split(',')
                      .map((m) => modeLabel(m.trim()))
                      .join(', ')}
                  </span>
                </div>
                <div className="record-card-row">
                  <span>Pays</span>
                  <span>{c.country || '—'}</span>
                </div>
                <div className="record-card-row">
                  <span>Contact</span>
                  <span>{c.contactEmail || c.contactName || '—'}</span>
                </div>
                <div className="record-card-actions">
                  <button
                    className="btn btn-outline btn-sm"
                    disabled={busyId === c.id}
                    onClick={() => toggleActive(c)}
                  >
                    {c.active ? 'Désactiver' : 'Activer'}
                  </button>
                  <button className="btn btn-outline btn-sm" onClick={() => openEdit(c)}>
                    Modifier
                  </button>
                  <button
                    className="btn btn-danger btn-sm"
                    disabled={busyId === c.id}
                    onClick={() => remove(c)}
                  >
                    Supprimer
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  )
}
