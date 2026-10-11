import { Fragment, useEffect, useMemo, useState } from 'react'
import api from '../services/api'

const TRANSPORT_MODES = [
  { value: 'SEA', label: 'Maritime' },
  { value: 'AIR', label: 'Aérien' },
  { value: 'ROAD', label: 'Routier' }
]

const CURRENCIES = ['EUR', 'USD', 'GBP', 'MAD', 'CHF', 'CAD', 'JPY']

const EMPTY_FORM = {
  carrierId: '',
  name: '',
  originCountry: '',
  destinationCountry: '',
  transportMode: 'ROAD',
  minWeightKg: '',
  maxWeightKg: '',
  baseRate: 0,
  currency: 'EUR',
  ratePerKg: 0,
  ratePerCbm: 0,
  transitDaysMin: '',
  transitDaysMax: '',
  co2EstimateKg: ''
}

export default function ShippingRates() {
  const [rates, setRates] = useState([])
  const [carriers, setCarriers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [search, setSearch] = useState('')
  const [filterMode, setFilterMode] = useState('')
  const [filterActive, setFilterActive] = useState('all')
  const [expandedId, setExpandedId] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [busyId, setBusyId] = useState(null)

  const load = () =>
    api
      .get('/shipping-rates', { params: { page: 0, size: 200 } })
      .then((res) => setRates(Array.isArray(res.data) ? res.data : res.data.content || []))
      .catch(() => setError('Impossible de charger les tarifs'))
      .finally(() => setLoading(false))

  useEffect(() => {
    load()
    api
      .get('/carriers')
      .then((res) => setCarriers(res.data))
      .catch(() => {})
  }, [])

  const filtered = useMemo(() => {
    const q = search.toLowerCase()
    return rates.filter((r) => {
      const matchSearch =
        !q ||
        `${r.name} ${r.originCountry} ${r.destinationCountry} ${r.carrierName || ''} ${r.carrierCode || ''}`
          .toLowerCase()
          .includes(q)
      const matchMode = !filterMode || r.transportMode === filterMode
      const matchActive = filterActive === 'all' || (filterActive === 'active' ? r.active : !r.active)
      return matchSearch && matchMode && matchActive
    })
  }, [rates, search, filterMode, filterActive])

  const carrierDisplay = (r) => r.carrierName || carriers.find((c) => c.id === r.carrierId)?.name || '—'

  const openCreate = () => {
    setEditing(null)
    setForm(EMPTY_FORM)
    setShowForm(true)
  }

  const openEdit = (r) => {
    setEditing(r)
    setForm({
      carrierId: r.carrierId || '',
      name: r.name,
      originCountry: r.originCountry,
      destinationCountry: r.destinationCountry,
      transportMode: r.transportMode,
      minWeightKg: r.minWeightKg ?? '',
      maxWeightKg: r.maxWeightKg ?? '',
      baseRate: r.baseRate,
      currency: r.currency,
      ratePerKg: r.ratePerKg,
      ratePerCbm: r.ratePerCbm,
      transitDaysMin: r.transitDaysMin ?? '',
      transitDaysMax: r.transitDaysMax ?? '',
      co2EstimateKg: r.co2EstimateKg ?? ''
    })
    setShowForm(true)
  }

  const closeForm = () => {
    setShowForm(false)
    setEditing(null)
    setForm(EMPTY_FORM)
  }

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })
  const setNum = (field) => (e) => setForm({ ...form, [field]: e.target.value === '' ? '' : parseFloat(e.target.value) })

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setNotice('')
    if (!form.carrierId || !form.name || !form.originCountry || !form.destinationCountry) {
      setError('Veuillez remplir tous les champs obligatoires')
      return
    }
    if (!form.baseRate || form.baseRate <= 0) {
      setError('Le tarif de base doit être supérieur à 0')
      return
    }
    setSubmitting(true)
    try {
      if (editing) {
        await api.put(`/shipping-rates/${editing.id}`, form)
        setNotice('Tarif mis à jour')
      } else {
        await api.post('/shipping-rates', form)
        setNotice('Tarif créé avec succès')
      }
      closeForm()
      await load()
    } catch (err) {
      setError(err.response?.data?.message || "L'enregistrement a échoué")
    } finally {
      setSubmitting(false)
    }
  }

  const toggleActive = async (r) => {
    setBusyId(r.id)
    try {
      await api.patch(`/shipping-rates/${r.id}/toggle`)
      await load()
    } catch {
      setError('Le changement de statut a échoué')
    } finally {
      setBusyId(null)
    }
  }

  const remove = async (r) => {
    if (!window.confirm(`Supprimer définitivement le tarif ${r.name} ?`)) return
    setBusyId(r.id)
    try {
      await api.delete(`/shipping-rates/${r.id}`)
      setNotice('Tarif supprimé')
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
          <h2>Gestion des tarifs</h2>
          <p>Tarifs négociés avec vos transporteurs, par route et mode de transport</p>
        </div>
        <div className="header-actions">
          <button className="btn btn-primary" onClick={openCreate}>
            + Ajouter un tarif
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      <div className="filter-bar">
        <input
          type="search"
          placeholder="Rechercher par nom, transporteur, origine, destination…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="search-input"
        />
        <select value={filterMode} onChange={(e) => setFilterMode(e.target.value)}>
          <option value="">Tous les modes</option>
          {TRANSPORT_MODES.map((m) => (
            <option key={m.value} value={m.value}>
              {m.label}
            </option>
          ))}
        </select>
        <select value={filterActive} onChange={(e) => setFilterActive(e.target.value)}>
          <option value="all">Tous les statuts</option>
          <option value="active">Actifs</option>
          <option value="inactive">Inactifs</option>
        </select>
        <span className="muted">{filtered.length} tarif(s)</span>
      </div>

      {showForm && (
        <div className="card">
          <div className="card-title">
            <h3>{editing ? 'Modifier le tarif' : 'Nouveau tarif'}</h3>
          </div>
          <form onSubmit={handleSubmit}>
            <div className="data-grid">
              <div className="form-field">
                <label>Transporteur *</label>
                <select value={form.carrierId} required onChange={set('carrierId')}>
                  <option value="">Sélectionner…</option>
                  {carriers.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name} ({c.code})
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-field">
                <label>Nom du tarif *</label>
                <input type="text" value={form.name} required onChange={set('name')} placeholder="TARIF STANDARD FR→DE" />
              </div>
              <div className="form-field">
                <label>Origine *</label>
                <input type="text" value={form.originCountry} required onChange={set('originCountry')} placeholder="France" />
              </div>
              <div className="form-field">
                <label>Destination *</label>
                <input type="text" value={form.destinationCountry} required onChange={set('destinationCountry')} placeholder="Allemagne" />
              </div>
              <div className="form-field">
                <label>Mode *</label>
                <select value={form.transportMode} required onChange={set('transportMode')}>
                  {TRANSPORT_MODES.map((m) => (
                    <option key={m.value} value={m.value}>
                      {m.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-field">
                <label>Tarif de base *</label>
                <input type="number" step="0.01" min="0.01" value={form.baseRate} required onChange={setNum('baseRate')} />
              </div>
              <div className="form-field">
                <label>Devise</label>
                <select value={form.currency} onChange={set('currency')}>
                  {CURRENCIES.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-field">
                <label>€/kg</label>
                <input type="number" step="0.01" min="0" value={form.ratePerKg ?? ''} onChange={setNum('ratePerKg')} />
              </div>
              <div className="form-field">
                <label>€/m³</label>
                <input type="number" step="0.01" min="0" value={form.ratePerCbm ?? ''} onChange={setNum('ratePerCbm')} />
              </div>
              <div className="form-field">
                <label>Poids min (kg)</label>
                <input type="number" step="0.1" min="0" value={form.minWeightKg} onChange={setNum('minWeightKg')} />
              </div>
              <div className="form-field">
                <label>Poids max (kg)</label>
                <input type="number" step="0.1" min="0" value={form.maxWeightKg} onChange={setNum('maxWeightKg')} />
              </div>
              <div className="form-field">
                <label>Transit min (jours)</label>
                <input type="number" min="1" value={form.transitDaysMin} onChange={setNum('transitDaysMin')} />
              </div>
              <div className="form-field">
                <label>Transit max (jours)</label>
                <input type="number" min="1" value={form.transitDaysMax} onChange={setNum('transitDaysMax')} />
              </div>
              <div className="form-field">
                <label>CO₂ estimé (kg)</label>
                <input type="number" step="0.1" min="0" value={form.co2EstimateKg} onChange={setNum('co2EstimateKg')} />
              </div>
            </div>
            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Enregistrement…' : editing ? 'Enregistrer' : 'Créer'}
              </button>
              <button type="button" className="btn btn-outline" onClick={closeForm}>
                Annuler
              </button>
            </div>
          </form>
        </div>
      )}

      {loading && (
        <div className="loading-spinner" aria-label="Chargement des tarifs…">
          Chargement…
        </div>
      )}

      <div className="card desktop-table">
        <div className="table-scroll">
          <table className="table table-hover">
            <thead>
              <tr>
                <th>Nom</th>
                <th>Transporteur</th>
                <th>Route</th>
                <th>Mode</th>
                <th>Tarif base</th>
                <th>€/kg</th>
                <th>Transit</th>
                <th>Statut</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((r) => (
                <Fragment key={r.id}>
                  <tr className="row-clickable" onClick={() => setExpandedId(expandedId === r.id ? null : r.id)}>
                    <td className="cell-strong">
                      <strong>{r.name}</strong>
                      <span className="muted block">{r.currency}</span>
                    </td>
                    <td>
                      {carrierDisplay(r)}
                      {r.carrierCode && <span className="muted block">{r.carrierCode}</span>}
                    </td>
                    <td>
                      {r.originCountry} <span className="muted">→</span> {r.destinationCountry}
                    </td>
                    <td>
                      <span className="badge badge-blue">{TRANSPORT_MODES.find((m) => m.value === r.transportMode)?.label || r.transportMode}</span>
                    </td>
                    <td>{r.baseRate.toFixed(2)} €</td>
                    <td>{r.ratePerKg > 0 ? `${r.ratePerKg.toFixed(2)} €` : '—'}</td>
                    <td>{r.transitDaysMin && r.transitDaysMax ? `${r.transitDaysMin}-${r.transitDaysMax}j` : '—'}</td>
                    <td>
                      <span className={`badge ${r.active ? 'badge-green' : 'badge-gray'}`}>{r.active ? 'Actif' : 'Inactif'}</span>
                    </td>
                    <td onClick={(e) => e.stopPropagation()}>
                      <div className="row-actions">
                        <button className="btn btn-outline btn-sm" disabled={busyId === r.id} onClick={() => toggleActive(r)}>
                          {r.active ? 'Désactiver' : 'Activer'}
                        </button>
                        <button className="btn btn-outline btn-sm" onClick={() => openEdit(r)}>
                          Modifier
                        </button>
                        <button className="btn btn-danger btn-sm" disabled={busyId === r.id} onClick={() => remove(r)}>
                          Supprimer
                        </button>
                      </div>
                    </td>
                  </tr>
                  {expandedId === r.id && (
                    <tr>
                      <td colSpan="9">
                        <div className="truck-card-grid">
                          <div className="couple-stat">
                            <span>Poids</span>
                            <b>{r.minWeightKg || '—'} — {r.maxWeightKg || '—'} kg</b>
                          </div>
                          <div className="couple-stat">
                            <span>Prix au m³</span>
                            <b>{r.ratePerCbm > 0 ? `${r.ratePerCbm.toFixed(2)} €` : '—'}</b>
                          </div>
                          <div className="couple-stat">
                            <span>CO₂ estimé</span>
                            <b>{r.co2EstimateKg ? `${r.co2EstimateKg} kg` : '—'}</b>
                          </div>
                          <div className="couple-stat">
                            <span>Validité</span>
                            <b>
                              {r.validFrom ? new Date(r.validFrom).toLocaleDateString('fr-FR') : '—'} →{' '}
                              {r.validUntil ? new Date(r.validUntil).toLocaleDateString('fr-FR') : '—'}
                            </b>
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
              {filtered.length === 0 && !error && !loading && (
                <tr>
                  <td colSpan="9" className="table-empty">
                    {rates.length === 0 ? 'Aucun tarif configuré' : 'Aucun résultat'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {!loading && (
        <div className="record-cards">
          {filtered.map((r) => (
            <div key={r.id} className="record-card">
              <div className="record-card-title">
                <strong>{r.name}</strong>
                <span className={`badge ${r.active ? 'badge-green' : 'badge-gray'}`}>{r.active ? 'Actif' : 'Inactif'}</span>
              </div>
              <div className="record-card-row">
                <span>Route</span>
                <span>
                  {r.originCountry} → {r.destinationCountry}
                </span>
              </div>
              <div className="record-card-row">
                <span>Tarif</span>
                <span>{r.baseRate.toFixed(2)} €</span>
              </div>
              <div className="record-card-actions">
                <button className="btn btn-outline btn-sm" onClick={() => openEdit(r)}>
                  Modifier
                </button>
                <button className="btn btn-danger btn-sm" onClick={() => remove(r)}>
                  Supprimer
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
