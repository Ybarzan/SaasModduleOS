import { useEffect, useState } from 'react'
import api from '../services/api'

const STATUS_CONFIG = {
  PENDING: { label: 'En attente', color: 'orange' },
  SUBMITTED: { label: 'Soumise', color: 'blue' },
  CONFIRMED: { label: 'Confirmée', color: 'green' },
  REJECTED: { label: 'Rejetée', color: 'red' },
  CANCELLED: { label: 'Annulée', color: 'gray' },
  COMPLETED: { label: 'Terminée', color: 'green' },
  FAILED: { label: 'Échouée', color: 'red' }
}

const EMPTY_FORM = {
  shipmentOrderId: '',
  carrierId: '',
  serviceType: '',
  specialInstructions: '',
  requestedPickupDate: ''
}

export default function CarrierBookings() {
  const [bookings, setBookings] = useState([])
  const [carriers, setCarriers] = useState([])
  const [shipments, setShipments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [busyId, setBusyId] = useState(null)

  const load = () =>
    api
      .get('/carrier-bookings', { params: { page: 0, size: 200 } })
      .then((res) => setBookings(Array.isArray(res.data) ? res.data : res.data.content || []))
      .catch(() => setError('Impossible de charger les réservations'))
      .finally(() => setLoading(false))

  useEffect(() => {
    load()
    api
      .get('/carriers')
      .then((res) => setCarriers(res.data))
      .catch(() => {})
    api
      .get('/shipments', { params: { page: 0, size: 200 } })
      .then((res) => setShipments(Array.isArray(res.data) ? res.data : res.data.content || []))
      .catch(() => {})
  }, [])

  const openCreate = () => {
    setForm(EMPTY_FORM)
    setShowForm(true)
  }

  const closeForm = () => {
    setShowForm(false)
    setForm(EMPTY_FORM)
  }

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  const handleCreate = async (e) => {
    e.preventDefault()
    setError('')
    setNotice('')
    if (!form.shipmentOrderId || !form.carrierId) {
      setError('Sélectionnez une expédition et un transporteur')
      return
    }
    setSubmitting(true)
    try {
      await api.post('/carrier-bookings', form)
      setNotice('Réservation créée')
      closeForm()
      await load()
    } catch (err) {
      setError(err.response?.data?.message || 'La création a échoué')
    } finally {
      setSubmitting(false)
    }
  }

  const submit = async (id) => {
    setBusyId(id)
    setError('')
    try {
      await api.post(`/carrier-bookings/${id}/submit`)
      setNotice('Réservation soumise au transporteur')
      await load()
    } catch (err) {
      setError(err.response?.data?.message || 'La soumission a échoué')
    } finally {
      setBusyId(null)
    }
  }

  const cancel = async (id) => {
    setBusyId(id)
    setError('')
    try {
      await api.post(`/carrier-bookings/${id}/cancel`)
      setNotice('Réservation annulée')
      await load()
    } catch (err) {
      setError(err.response?.data?.message || "L'annulation a échoué")
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Réservations transporteurs</h2>
          <p>Gérez les réservations auprès de vos transporteurs</p>
        </div>
        <div className="header-actions">
          <button className="btn btn-primary" onClick={openCreate}>
            + Nouvelle réservation
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      {showForm && (
        <div className="card">
          <div className="card-title">
            <h3>Nouvelle réservation</h3>
          </div>
          <form onSubmit={handleCreate}>
            <div className="data-grid">
              <div className="form-field">
                <label>Expédition *</label>
                <select value={form.shipmentOrderId} required onChange={set('shipmentOrderId')}>
                  <option value="">Sélectionner…</option>
                  {shipments.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.orderNumber} — {(s.goodsDescription || '').slice(0, 40)}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-field">
                <label>Transporteur *</label>
                <select value={form.carrierId} required onChange={set('carrierId')}>
                  <option value="">Sélectionner…</option>
                  {carriers
                    .filter((c) => c.active)
                    .map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name} ({c.code})
                      </option>
                    ))}
                </select>
              </div>
              <div className="form-field">
                <label>Type de service</label>
                <input
                  type="text"
                  value={form.serviceType}
                  onChange={set('serviceType')}
                  placeholder="EXPRESS, STANDARD, ECONOMY…"
                />
              </div>
              <div className="form-field">
                <label>Date d'enlèvement souhaitée</label>
                <input
                  type="date"
                  value={form.requestedPickupDate}
                  onChange={set('requestedPickupDate')}
                />
              </div>
            </div>
            <div className="form-field" style={{ marginTop: 14 }}>
              <label>Instructions spéciales</label>
              <textarea
                rows={3}
                value={form.specialInstructions}
                onChange={set('specialInstructions')}
              />
            </div>
            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Création…' : 'Créer'}
              </button>
              <button type="button" className="btn btn-outline" onClick={closeForm}>
                Annuler
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="chip-row" style={{ marginBottom: 20 }}>
        {Object.entries(STATUS_CONFIG).map(([key, cfg]) => (
          <span key={key} className={`badge badge-${cfg.color}`}>
            {cfg.label} · {bookings.filter((b) => b.carrierBookingStatus === key).length}
          </span>
        ))}
      </div>

      {loading && (
        <div className="loading-spinner" aria-label="Chargement des réservations…">
          Chargement…
        </div>
      )}

      <div className="card desktop-table">
        <div className="card-title">
          <h3>Réservations</h3>
          <span className="muted">{bookings.length} réservation(s)</span>
        </div>
        <div className="table-scroll">
          <table className="table table-hover">
            <thead>
              <tr>
                <th>Statut</th>
                <th>Transporteur</th>
                <th>Expédition</th>
                <th>Référence</th>
                <th>Coût estimé</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {bookings.map((b) => {
                const cfg = STATUS_CONFIG[b.carrierBookingStatus] || {
                  label: b.carrierBookingStatus,
                  color: 'gray'
                }
                return (
                  <tr key={b.id}>
                    <td>
                      <span className={`badge badge-${cfg.color}`}>{cfg.label}</span>
                    </td>
                    <td>{b.carrier?.name || '—'}</td>
                    <td className="muted">{b.shipmentOrder?.orderNumber || '—'}</td>
                    <td className="muted">
                      {b.carrierReference || '—'}
                      {b.simulated && (
                        <span
                          className="badge badge-gray"
                          style={{ marginLeft: 6 }}
                          title="Réponse simulée : aucune clé API transporteur configurée"
                        >
                          Simulé
                        </span>
                      )}
                    </td>
                    <td>
                      {b.quotedCost != null
                        ? `${b.quotedCost.toFixed(2)} ${b.quotedCostCurrency || 'EUR'}`
                        : '—'}
                    </td>
                    <td>
                      <div className="row-actions">
                        {b.carrierBookingStatus === 'PENDING' && (
                          <button
                            className="btn btn-outline btn-sm"
                            disabled={busyId === b.id}
                            onClick={() => submit(b.id)}
                          >
                            Soumettre
                          </button>
                        )}
                        {(b.carrierBookingStatus === 'PENDING' ||
                          b.carrierBookingStatus === 'SUBMITTED') && (
                          <button
                            className="btn btn-danger btn-sm"
                            disabled={busyId === b.id}
                            onClick={() => cancel(b.id)}
                          >
                            Annuler
                          </button>
                        )}
                        {b.carrierBookingStatus === 'CONFIRMED' && b.estimatedDeliveryDate && (
                          <span className="muted">
                            Livraison{' '}
                            {new Date(b.estimatedDeliveryDate).toLocaleDateString('fr-FR')}
                          </span>
                        )}
                        {b.carrierBookingStatus === 'FAILED' && b.errorMessage && (
                          <span className="text-danger" title={b.errorMessage}>
                            {b.errorMessage.slice(0, 30)}
                          </span>
                        )}
                      </div>
                    </td>
                  </tr>
                )
              })}
              {bookings.length === 0 && !error && !loading && (
                <tr>
                  <td colSpan="6" className="table-empty">
                    Aucune réservation
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {!loading && (
        <div className="record-cards">
          {bookings.length === 0 ? (
            <p className="muted table-empty">Aucune réservation</p>
          ) : (
            bookings.map((b) => {
              const cfg = STATUS_CONFIG[b.carrierBookingStatus] || {
                label: b.carrierBookingStatus,
                color: 'gray'
              }
              return (
                <div key={b.id} className="record-card">
                  <div className="record-card-title">
                    <strong>{b.carrier?.name || '—'}</strong>
                    <span className={`badge badge-${cfg.color}`}>{cfg.label}</span>
                  </div>
                  <div className="record-card-row">
                    <span>Expédition</span>
                    <span>{b.shipmentOrder?.orderNumber || '—'}</span>
                  </div>
                  <div className="record-card-row">
                    <span>Référence</span>
                    <span>{b.carrierReference || '—'}</span>
                  </div>
                  <div className="record-card-row">
                    <span>Coût</span>
                    <span>
                      {b.quotedCost != null
                        ? `${b.quotedCost.toFixed(2)} ${b.quotedCostCurrency || 'EUR'}`
                        : '—'}
                    </span>
                  </div>
                  <div className="record-card-actions">
                    {b.carrierBookingStatus === 'PENDING' && (
                      <button
                        className="btn btn-outline btn-sm"
                        disabled={busyId === b.id}
                        onClick={() => submit(b.id)}
                      >
                        Soumettre
                      </button>
                    )}
                    {(b.carrierBookingStatus === 'PENDING' ||
                      b.carrierBookingStatus === 'SUBMITTED') && (
                      <button
                        className="btn btn-danger btn-sm"
                        disabled={busyId === b.id}
                        onClick={() => cancel(b.id)}
                      >
                        Annuler
                      </button>
                    )}
                  </div>
                </div>
              )
            })
          )}
        </div>
      )}
    </div>
  )
}
