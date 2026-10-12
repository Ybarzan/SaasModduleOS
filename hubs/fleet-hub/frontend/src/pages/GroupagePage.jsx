import { useEffect, useState } from 'react'
import api from '../services/api'

const STATUS_CONFIG = {
  PLANNED: { label: 'Planifié', color: 'gray' },
  FORMING: { label: 'En constitution', color: 'blue' },
  BOOKED: { label: 'Réservé', color: 'orange' },
  DEPARTED: { label: 'Départ', color: 'blue' },
  DELIVERED: { label: 'Livré', color: 'green' },
  CANCELLED: { label: 'Annulé', color: 'red' }
}

const STATUS_FLOW = ['PLANNED', 'FORMING', 'BOOKED', 'DEPARTED', 'DELIVERED']
const MODE_ICON = { SEA: '🚢', AIR: '✈️', ROAD: '🚛' }

const fmtDate = (v) => (v ? new Date(v).toLocaleDateString('fr-FR') : '—')
const nextStatus = (current) => {
  if (current === 'CANCELLED') return null
  const idx = STATUS_FLOW.indexOf(current)
  return idx >= 0 && idx < STATUS_FLOW.length - 1 ? STATUS_FLOW[idx + 1] : null
}
const utilization = (booked, capacity) =>
  capacity && capacity > 0 ? Math.min((booked / capacity) * 100, 100) : 0

const EMPTY_FORM = {
  name: '',
  transportMode: 'ROAD',
  carrierName: '',
  origin: '',
  destination: '',
  capacityWeightKg: '',
  capacityVolumeM3: '',
  plannedDeparture: '',
  plannedArrival: ''
}

export default function GroupagePage() {
  const [groupages, setGroupages] = useState([])
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [detailId, setDetailId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [memberForm, setMemberForm] = useState({
    shipmentOrderId: '',
    externalCompany: '',
    reference: '',
    weightKg: '',
    volumeM3: ''
  })
  const [busyId, setBusyId] = useState(null)

  const load = () => {
    setLoading(true)
    api
      .get('/groupages')
      .then((res) => setGroupages(res.data || []))
      .catch(() => setError('Impossible de charger les groupages'))
      .finally(() => setLoading(false))
    api
      .get('/groupages/stats')
      .then((res) => setStats(res.data))
      .catch(() => {})
  }

  useEffect(() => {
    load()
  }, [])

  const loadDetail = (id) => {
    api
      .get(`/groupages/${id}`)
      .then((res) => setDetail(res.data))
      .catch(() => setError('Impossible de charger le détail du groupage'))
  }

  const openDetail = (id) => {
    setDetailId(id)
    loadDetail(id)
  }

  const closeDetail = () => {
    setDetailId(null)
    setDetail(null)
    setMemberForm({
      shipmentOrderId: '',
      externalCompany: '',
      reference: '',
      weightKg: '',
      volumeM3: ''
    })
  }

  const handleCreate = async (e) => {
    e.preventDefault()
    setError('')
    setNotice('')
    if (!form.name) return
    setSubmitting(true)
    try {
      await api.post('/groupages', {
        name: form.name,
        transportMode: form.transportMode,
        carrierName: form.carrierName || undefined,
        origin: form.origin || undefined,
        destination: form.destination || undefined,
        capacityWeightKg: form.capacityWeightKg ? Number(form.capacityWeightKg) : undefined,
        capacityVolumeM3: form.capacityVolumeM3 ? Number(form.capacityVolumeM3) : undefined,
        plannedDeparture: form.plannedDeparture || undefined,
        plannedArrival: form.plannedArrival || undefined
      })
      setNotice('Groupage créé')
      setShowForm(false)
      setForm(EMPTY_FORM)
      load()
    } catch {
      setError('Erreur lors de la création')
    } finally {
      setSubmitting(false)
    }
  }

  const advanceStatus = async (g) => {
    const status = nextStatus(g.status)
    if (!status) return
    setBusyId(g.id)
    try {
      await api.post(`/groupages/${g.id}/status`, { status })
      setNotice('Statut mis à jour')
      load()
      if (detailId === g.id) loadDetail(g.id)
    } catch {
      setError('Transition de statut impossible')
    } finally {
      setBusyId(null)
    }
  }

  const remove = async (g) => {
    if (!window.confirm(`Supprimer le groupage ${g.reference} ?`)) return
    setBusyId(g.id)
    try {
      await api.delete(`/groupages/${g.id}`)
      setNotice('Groupage supprimé')
      load()
    } catch {
      setError('Suppression impossible (statut engagé)')
    } finally {
      setBusyId(null)
    }
  }

  const submitMember = async (e) => {
    e.preventDefault()
    setError('')
    if (!memberForm.weightKg || !memberForm.volumeM3) return
    try {
      await api.post(`/groupages/${detailId}/members`, {
        shipmentOrderId: memberForm.shipmentOrderId || undefined,
        externalCompany: memberForm.externalCompany || undefined,
        reference: memberForm.reference || undefined,
        weightKg: Number(memberForm.weightKg),
        volumeM3: Number(memberForm.volumeM3)
      })
      setMemberForm({
        shipmentOrderId: '',
        externalCompany: '',
        reference: '',
        weightKg: '',
        volumeM3: ''
      })
      loadDetail(detailId)
      load()
      setNotice('Expédition ajoutée au groupage')
    } catch {
      setError('Capacité dépassée ou données invalides')
    }
  }

  const removeMember = async (memberId) => {
    try {
      await api.delete(`/groupages/${detailId}/members/${memberId}`)
      loadDetail(detailId)
      load()
      setNotice('Expédition retirée')
    } catch {
      setError('Retrait impossible')
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>📦 Groupage & co-loading</h2>
          <p>Consolidez plusieurs exportateurs sur un même transport pour mutualiser les coûts</p>
        </div>
        <div className="header-actions">
          <button className="btn btn-primary" onClick={() => setShowForm(true)}>
            + Nouveau groupage
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      {stats && (
        <div className="chip-row" style={{ marginBottom: 20 }}>
          <span className="badge badge-gray">Total · {stats.total ?? 0}</span>
          {['PLANNED', 'FORMING', 'BOOKED', 'DEPARTED', 'DELIVERED', 'CANCELLED'].map((key) => (
            <span key={key} className={`badge badge-${STATUS_CONFIG[key].color}`}>
              {STATUS_CONFIG[key].label} · {stats[key] ?? 0}
            </span>
          ))}
        </div>
      )}

      {showForm && (
        <div className="card">
          <div className="card-title">
            <h3>Nouveau groupage</h3>
          </div>
          <form onSubmit={handleCreate}>
            <div className="data-grid">
              <div className="form-field">
                <label>Nom du groupage *</label>
                <input
                  type="text"
                  value={form.name}
                  required
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                />
              </div>
              <div className="form-field">
                <label>Transporteur</label>
                <input
                  type="text"
                  value={form.carrierName}
                  onChange={(e) => setForm({ ...form, carrierName: e.target.value })}
                />
              </div>
              <div className="form-field">
                <label>Origine</label>
                <input
                  type="text"
                  value={form.origin}
                  onChange={(e) => setForm({ ...form, origin: e.target.value })}
                />
              </div>
              <div className="form-field">
                <label>Destination</label>
                <input
                  type="text"
                  value={form.destination}
                  onChange={(e) => setForm({ ...form, destination: e.target.value })}
                />
              </div>
              <div className="form-field">
                <label>Capacité poids (kg)</label>
                <input
                  type="number"
                  min="0"
                  value={form.capacityWeightKg}
                  onChange={(e) => setForm({ ...form, capacityWeightKg: e.target.value })}
                />
              </div>
              <div className="form-field">
                <label>Capacité volume (m³)</label>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.capacityVolumeM3}
                  onChange={(e) => setForm({ ...form, capacityVolumeM3: e.target.value })}
                />
              </div>
              <div className="form-field">
                <label>Départ prévu</label>
                <input
                  type="date"
                  value={form.plannedDeparture}
                  onChange={(e) => setForm({ ...form, plannedDeparture: e.target.value })}
                />
              </div>
              <div className="form-field">
                <label>Arrivée prévue</label>
                <input
                  type="date"
                  value={form.plannedArrival}
                  onChange={(e) => setForm({ ...form, plannedArrival: e.target.value })}
                />
              </div>
            </div>
            <div className="form-field" style={{ marginTop: 14 }}>
              <label>Mode de transport</label>
              <div className="chip-row">
                {['ROAD', 'SEA', 'AIR'].map((mode) => (
                  <button
                    key={mode}
                    type="button"
                    className={`chip ${form.transportMode === mode ? 'active' : ''}`}
                    onClick={() => setForm({ ...form, transportMode: mode })}
                  >
                    {MODE_ICON[mode]}{' '}
                    {mode === 'SEA' ? 'Maritime' : mode === 'AIR' ? 'Aérien' : 'Routier'}
                  </button>
                ))}
              </div>
            </div>
            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Création…' : 'Créer'}
              </button>
              <button type="button" className="btn btn-outline" onClick={() => setShowForm(false)}>
                Annuler
              </button>
            </div>
          </form>
        </div>
      )}

      {detailId && detail && (
        <div className="card">
          <div className="card-title">
            <h3>
              {detail.name} <span className="muted">{detail.reference}</span>{' '}
              <span className={`badge badge-${STATUS_CONFIG[detail.status].color}`}>
                {STATUS_CONFIG[detail.status].label}
              </span>
            </h3>
            <button className="btn btn-outline btn-sm" onClick={closeDetail}>
              Fermer
            </button>
          </div>
          <div className="truck-card-grid">
            <div className="couple-stat">
              <span>⚖️ Poids</span>
              <b>
                {detail.bookedWeightKg} / {detail.capacityWeightKg ?? '∞'} kg
              </b>
            </div>
            <div className="couple-stat">
              <span>📐 Volume</span>
              <b>
                {detail.bookedVolumeM3} / {detail.capacityVolumeM3 ?? '∞'} m³
              </b>
            </div>
            <div className="couple-stat">
              <span>👥 Expéditions</span>
              <b>{detail.memberCount}</b>
            </div>
            <div className="couple-stat">
              <span>📅 Départ</span>
              <b>{fmtDate(detail.plannedDeparture)}</b>
            </div>
          </div>

          <h4 style={{ marginTop: 16 }}>Expéditions consolidées</h4>
          {detail.members.length === 0 ? (
            <p className="muted table-empty">Aucune expédition dans ce groupage</p>
          ) : (
            <div className="table-scroll">
              <table className="table">
                <thead>
                  <tr>
                    <th>Référence</th>
                    <th>Exportateur</th>
                    <th>Poids</th>
                    <th>Volume</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.members.map((m) => (
                    <tr key={m.id}>
                      <td className="cell-strong">{m.reference || '—'}</td>
                      <td className="muted">{m.externalCompany || 'Interne'}</td>
                      <td>{m.weightKg} kg</td>
                      <td>{m.volumeM3} m³</td>
                      <td>
                        <button
                          className="btn btn-danger btn-sm"
                          onClick={() => removeMember(m.id)}
                        >
                          Retirer
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <h4 style={{ marginTop: 16 }}>Ajouter une expédition</h4>
          <form onSubmit={submitMember}>
            <div className="form-row" style={{ gridTemplateColumns: 'repeat(5, 1fr)' }}>
              <input
                placeholder="Référence"
                value={memberForm.reference}
                onChange={(e) => setMemberForm({ ...memberForm, reference: e.target.value })}
              />
              <input
                placeholder="Exportateur externe"
                value={memberForm.externalCompany}
                onChange={(e) => setMemberForm({ ...memberForm, externalCompany: e.target.value })}
              />
              <input
                placeholder="Poids (kg)"
                type="number"
                min="0"
                required
                value={memberForm.weightKg}
                onChange={(e) => setMemberForm({ ...memberForm, weightKg: e.target.value })}
              />
              <input
                placeholder="Volume (m³)"
                type="number"
                min="0"
                step="0.01"
                required
                value={memberForm.volumeM3}
                onChange={(e) => setMemberForm({ ...memberForm, volumeM3: e.target.value })}
              />
              <button type="submit" className="btn btn-primary">
                + Ajouter
              </button>
            </div>
          </form>
        </div>
      )}

      {loading && (
        <div className="loading-spinner" aria-label="Chargement des groupages…">
          Chargement…
        </div>
      )}

      {groupages.length === 0 && !loading ? (
        <div className="card" style={{ textAlign: 'center', padding: 40 }}>
          <p className="muted">
            Aucun groupage. Créez un premier groupage pour consolider vos expéditions.
          </p>
        </div>
      ) : (
        groupages.map((g) => {
          const cfg = STATUS_CONFIG[g.status]
          const wPct = utilization(g.bookedWeightKg, g.capacityWeightKg)
          const vPct = utilization(g.bookedVolumeM3, g.capacityVolumeM3)
          const next = nextStatus(g.status)
          return (
            <div key={g.id} className="card">
              <div className="couple-card-top">
                <div>
                  <strong>
                    {MODE_ICON[g.transportMode] || '🚛'} {g.name}
                  </strong>
                  <span className="muted"> {g.reference}</span>
                  <span className={`badge badge-${cfg.color}`} style={{ marginLeft: 8 }}>
                    {cfg.label}
                  </span>
                  <div className="muted">
                    {g.origin || '—'} → {g.destination || '—'}
                    {g.carrierName && ` · ${g.carrierName}`} · {fmtDate(g.plannedDeparture)}
                  </div>
                </div>
                <div className="row-actions">
                  <button className="btn btn-outline btn-sm" onClick={() => openDetail(g.id)}>
                    Détail & membres
                  </button>
                  {next && (
                    <button
                      className="btn btn-primary btn-sm"
                      disabled={busyId === g.id}
                      onClick={() => advanceStatus(g)}
                    >
                      {STATUS_CONFIG[next].label}
                    </button>
                  )}
                  <button
                    className="btn btn-danger btn-sm"
                    disabled={busyId === g.id}
                    onClick={() => remove(g)}
                  >
                    Supprimer
                  </button>
                </div>
              </div>
              <div className="truck-card-grid">
                <div className="couple-stat">
                  <span>
                    ⚖️ Poids {g.bookedWeightKg} / {g.capacityWeightKg ?? '∞'} kg
                  </span>
                  <div className="progress-track">
                    <div className="progress-fill" style={{ width: `${wPct}%` }} />
                  </div>
                </div>
                <div className="couple-stat">
                  <span>
                    📐 Volume {g.bookedVolumeM3} / {g.capacityVolumeM3 ?? '∞'} m³
                  </span>
                  <div className="progress-track">
                    <div className="progress-fill" style={{ width: `${vPct}%` }} />
                  </div>
                </div>
              </div>
            </div>
          )
        })
      )}
    </div>
  )
}
