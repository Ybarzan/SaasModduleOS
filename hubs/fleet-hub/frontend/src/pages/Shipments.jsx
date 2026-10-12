import { Fragment, useEffect, useState } from 'react'
import api from '../services/api'
import LiveTrackingPanel from '../components/LiveTrackingPanel'

const LOGISTIX_URL = import.meta.env.VITE_LOGISTIX_URL || ''

const STATUS_CONFIG = {
  DRAFT: { label: 'Brouillon', color: 'gray' },
  QUOTED: { label: 'Devisé', color: 'blue' },
  BOOKED: { label: 'Réservé', color: 'blue' },
  IN_TRANSIT: { label: 'En transit', color: 'orange' },
  DELIVERED: { label: 'Livré', color: 'green' },
  CANCELLED: { label: 'Annulé', color: 'red' }
}

const INCOTERMS = ['EXW', 'FCA', 'FAS', 'FOB', 'CFR', 'CIF', 'CPT', 'CIP', 'DAP', 'DPU', 'DDP']

const EMPTY_FORM = {
  shipperName: '',
  shipperAddress: '',
  shipperCity: '',
  shipperCountry: '',
  shipperPostalCode: '',
  consigneeName: '',
  consigneeAddress: '',
  consigneeCity: '',
  consigneeCountry: '',
  consigneePostalCode: '',
  goodsDescription: '',
  goodsValue: 0,
  currency: 'EUR',
  weightKg: 0,
  volumeM3: 0,
  packagesCount: 1,
  hsCode: '',
  incotermCode: '',
  isDangerous: false,
  requestedPickupDate: '',
  carrierId: '',
  shippingRateId: '',
  fleetHubTruckRegistration: '',
  items: []
}

function AddressFields({ title, data, onChange }) {
  return (
    <div>
      <h4>📍 {title}</h4>
      <div className="data-grid">
        <div className="form-field">
          <label>Nom / Société</label>
          <input type="text" value={data.name} onChange={(e) => onChange('name', e.target.value)} />
        </div>
        <div className="form-field">
          <label>Adresse</label>
          <input
            type="text"
            value={data.address}
            onChange={(e) => onChange('address', e.target.value)}
          />
        </div>
        <div className="form-field">
          <label>Ville</label>
          <input type="text" value={data.city} onChange={(e) => onChange('city', e.target.value)} />
        </div>
        <div className="form-field">
          <label>Pays</label>
          <input
            type="text"
            value={data.country}
            onChange={(e) => onChange('country', e.target.value)}
          />
        </div>
        <div className="form-field">
          <label>Code postal</label>
          <input
            type="text"
            value={data.postalCode}
            onChange={(e) => onChange('postalCode', e.target.value)}
          />
        </div>
      </div>
    </div>
  )
}

function TrackingTimeline({ events }) {
  if (!events || events.length === 0) return <p className="muted">Aucun événement de suivi</p>
  return (
    <div className="timeline">
      {events.map((event, idx) => {
        const cfg = STATUS_CONFIG[event.status] || { label: event.status, color: 'gray' }
        const isLast = idx === events.length - 1
        return (
          <div key={event.id || idx} className="timeline-item">
            <span className={`timeline-dot ${isLast ? 'done' : ''}`} />
            <div className="timeline-body">
              <span className={`badge badge-${cfg.color}`}>{cfg.label}</span>
              <span className="timeline-time">
                {new Date(event.eventTime).toLocaleString('fr-FR')}
              </span>
              {event.dataSource === 'MANUAL' && (
                <span className="badge badge-gray" style={{ marginLeft: 6 }}>
                  Manuel
                </span>
              )}
              {event.location && <div className="muted">📍 {event.location}</div>}
              {event.description && <p className="muted">{event.description}</p>}
            </div>
          </div>
        )
      })}
    </div>
  )
}

function ShipmentReceivingSection({ shipmentId }) {
  if (!LOGISTIX_URL) return null
  return (
    <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px dashed var(--border)' }}>
      <h4>📋 Réception</h4>
      <p className="muted" style={{ marginBottom: 8 }}>
        La réception des marchandises se gère désormais dans Logistix.
      </p>
      <a
        href={`${LOGISTIX_URL}/receivings?shipmentId=${shipmentId}`}
        target="_blank"
        rel="noopener noreferrer"
        className="btn btn-outline btn-sm"
      >
        Ouvrir dans Logistix ↗
      </a>
    </div>
  )
}

export default function Shipments() {
  const [shipments, setShipments] = useState([])
  const [carriers, setCarriers] = useState([])
  const [fleetVehicles, setFleetVehicles] = useState([])
  const [rates, setRates] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [expandedId, setExpandedId] = useState(null)
  const [busyId, setBusyId] = useState(null)
  const [showRateSearch, setShowRateSearch] = useState(false)
  const [showItemsForm, setShowItemsForm] = useState(false)
  const [newItem, setNewItem] = useState({ sku: '', name: '', quantity: 1, unit: 'PCS' })
  const [clientLinks, setClientLinks] = useState({})
  const [copiedLinkId, setCopiedLinkId] = useState(null)
  const [exportingId, setExportingId] = useState(null)

  const load = () => {
    setLoading(true)
    api
      .get('/shipments', { params: { page: 0, size: 200 } })
      .then((res) => setShipments(Array.isArray(res.data) ? res.data : res.data.content || []))
      .catch(() => setError('Impossible de charger les expéditions'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    api
      .get('/carriers')
      .then((res) => setCarriers(res.data || []))
      .catch(() => {})
    api
      .get('/fleethub/vehicles')
      .then((res) => setFleetVehicles(res.data || []))
      .catch(() => {})
    api
      .get('/shipping-rates')
      .then((res) => setRates(Array.isArray(res.data) ? res.data : res.data.content || []))
      .catch(() => {})
  }, [])

  const totalShipments = shipments.length
  const inTransit = shipments.filter((s) => s.status === 'IN_TRANSIT').length
  const delivered = shipments.filter((s) => s.status === 'DELIVERED').length
  const drafts = shipments.filter((s) => s.status === 'DRAFT').length

  const matchingRates =
    form.shipperCountry && form.consigneeCountry && showRateSearch
      ? rates.filter(
          (r) =>
            r.originCountry === form.shipperCountry &&
            r.destinationCountry === form.consigneeCountry &&
            r.active &&
            (!form.weightKg || !r.maxWeightKg || form.weightKg <= r.maxWeightKg)
        )
      : []

  const openCreate = () => {
    setForm(EMPTY_FORM)
    setShowForm(true)
    setShowRateSearch(false)
    setShowItemsForm(false)
  }

  const setShipper = (field, value) =>
    setForm({ ...form, [`shipper${field.charAt(0).toUpperCase()}${field.slice(1)}`]: value })
  const setConsignee = (field, value) =>
    setForm({ ...form, [`consignee${field.charAt(0).toUpperCase()}${field.slice(1)}`]: value })

  const addItem = () => {
    if (!newItem.sku && !newItem.name) return
    setForm({ ...form, items: [...form.items, newItem] })
    setNewItem({ sku: '', name: '', quantity: 1, unit: 'PCS' })
  }

  const removeItem = (idx) => setForm({ ...form, items: form.items.filter((_, i) => i !== idx) })

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setNotice('')
    if (!form.shipperName.trim() || !form.consigneeName.trim()) {
      setError("Le nom de l'expéditeur et du destinataire sont obligatoires")
      return
    }
    setSubmitting(true)
    try {
      await api.post('/shipments', {
        ...form,
        items: form.items.length > 0 ? form.items : undefined
      })
      setNotice('Expédition créée')
      setShowForm(false)
      setForm(EMPTY_FORM)
      load()
    } catch {
      setError('Erreur lors de la création')
    } finally {
      setSubmitting(false)
    }
  }

  const updateStatus = async (id, status) => {
    setBusyId(id)
    try {
      await api.patch(`/shipments/${id}/status`, { status })
      setNotice('Statut mis à jour')
      load()
    } catch {
      setError('Erreur lors de la mise à jour du statut')
    } finally {
      setBusyId(null)
    }
  }

  const remove = async (id) => {
    if (!window.confirm('Supprimer définitivement cette expédition ?')) return
    setBusyId(id)
    try {
      await api.delete(`/shipments/${id}`)
      setNotice('Expédition supprimée')
      load()
    } catch {
      setError('Erreur lors de la suppression')
    } finally {
      setBusyId(null)
    }
  }

  const exportDoc = async (id, orderNumber, docType, filenamePrefix) => {
    setExportingId(`${id}-${docType}`)
    try {
      const res = await api.get(`/documents/shipments/${id}/${docType}`, { responseType: 'blob' })
      const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
      const a = document.createElement('a')
      a.href = url
      a.download = `${filenamePrefix}-${orderNumber}.pdf`
      a.click()
      window.URL.revokeObjectURL(url)
    } catch {
      setError("Erreur lors de l'export du document")
    } finally {
      setExportingId(null)
    }
  }

  const exportCsv = async () => {
    try {
      const res = await api.get('/export/shipments', { responseType: 'blob' })
      const url = window.URL.createObjectURL(new Blob([res.data], { type: 'text/csv' }))
      const a = document.createElement('a')
      a.href = url
      a.download = 'shipments_export.csv'
      a.click()
      window.URL.revokeObjectURL(url)
    } catch {
      setError("Erreur lors de l'export CSV")
    }
  }

  const generateShareLink = async (shipmentId) => {
    try {
      const res = await api.post('/shared', { shipmentId })
      const url = `${window.location.origin}${res.data.url}`
      setClientLinks((prev) => ({ ...prev, [shipmentId]: url }))
      navigator.clipboard?.writeText(url)
      setCopiedLinkId(shipmentId)
      setTimeout(() => setCopiedLinkId((c) => (c === shipmentId ? null : c)), 2000)
      setNotice('Lien client copié !')
    } catch {
      setError('Erreur lors de la création du lien client')
    }
  }

  const copyLink = (shipmentId) => {
    navigator.clipboard?.writeText(clientLinks[shipmentId])
    setCopiedLinkId(shipmentId)
    setTimeout(() => setCopiedLinkId((c) => (c === shipmentId ? null : c)), 2000)
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Expéditions</h2>
          <p>Gérez vos envois et suivez leur progression</p>
        </div>
        <div className="header-actions">
          <button className="btn btn-outline" onClick={exportCsv}>
            ⬇ Exporter CSV
          </button>
          <button
            className="btn btn-primary"
            onClick={() => (showForm ? setShowForm(false) : openCreate())}
          >
            + Nouvelle expédition
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      <div className="north-star-grid">
        <div className="stat-card blue">
          <div className="stat-icon">📦</div>
          <div className="stat-body">
            <div className="stat-label">Total</div>
            <div className="stat-value">{totalShipments}</div>
          </div>
        </div>
        <div className="stat-card purple">
          <div className="stat-icon">🚚</div>
          <div className="stat-body">
            <div className="stat-label">En transit</div>
            <div className="stat-value">{inTransit}</div>
          </div>
        </div>
        <div className="stat-card green">
          <div className="stat-icon">✅</div>
          <div className="stat-body">
            <div className="stat-label">Livrées</div>
            <div className="stat-value">{delivered}</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon">⚪</div>
          <div className="stat-body">
            <div className="stat-label">Brouillons</div>
            <div className="stat-value">{drafts}</div>
          </div>
        </div>
      </div>

      {showForm && (
        <div className="card">
          <div className="card-title">
            <h3>Nouvelle expédition</h3>
          </div>
          <form onSubmit={handleSubmit}>
            <AddressFields
              title="Expéditeur"
              data={{
                name: form.shipperName,
                address: form.shipperAddress,
                city: form.shipperCity,
                country: form.shipperCountry,
                postalCode: form.shipperPostalCode
              }}
              onChange={setShipper}
            />
            <div style={{ textAlign: 'center', margin: '10px 0', color: 'var(--muted)' }}>↓</div>
            <AddressFields
              title="Destinataire"
              data={{
                name: form.consigneeName,
                address: form.consigneeAddress,
                city: form.consigneeCity,
                country: form.consigneeCountry,
                postalCode: form.consigneePostalCode
              }}
              onChange={setConsignee}
            />

            <h4 style={{ marginTop: 20 }}>📦 Marchandises</h4>
            <div className="data-grid">
              <div className="form-field">
                <label>Description</label>
                <input
                  type="text"
                  value={form.goodsDescription}
                  onChange={(e) => setForm({ ...form, goodsDescription: e.target.value })}
                />
              </div>
              <div className="form-field">
                <label>Valeur (€)</label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={form.goodsValue || ''}
                  onChange={(e) =>
                    setForm({ ...form, goodsValue: parseFloat(e.target.value) || 0 })
                  }
                />
              </div>
              <div className="form-field">
                <label>Poids (kg)</label>
                <input
                  type="number"
                  step="0.1"
                  min="0"
                  value={form.weightKg || ''}
                  onChange={(e) => setForm({ ...form, weightKg: parseFloat(e.target.value) || 0 })}
                />
              </div>
              <div className="form-field">
                <label>Volume (m³)</label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={form.volumeM3 || ''}
                  onChange={(e) => setForm({ ...form, volumeM3: parseFloat(e.target.value) || 0 })}
                />
              </div>
              <div className="form-field">
                <label>Colis</label>
                <input
                  type="number"
                  min="1"
                  value={form.packagesCount || ''}
                  onChange={(e) =>
                    setForm({ ...form, packagesCount: parseInt(e.target.value) || 1 })
                  }
                />
              </div>
              <div className="form-field">
                <label>Code SH</label>
                <input
                  type="text"
                  value={form.hsCode}
                  onChange={(e) => setForm({ ...form, hsCode: e.target.value })}
                />
              </div>
              <div className="form-field">
                <label>Incoterm</label>
                <select
                  value={form.incotermCode}
                  onChange={(e) => setForm({ ...form, incotermCode: e.target.value })}
                >
                  <option value="">Incoterm</option>
                  {INCOTERMS.map((i) => (
                    <option key={i} value={i}>
                      {i}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-field">
                <label>Enlèvement souhaité</label>
                <input
                  type="date"
                  value={form.requestedPickupDate}
                  onChange={(e) => setForm({ ...form, requestedPickupDate: e.target.value })}
                />
              </div>
            </div>
            <div className="form-checkbox-row">
              <input
                type="checkbox"
                id="dangerous"
                checked={form.isDangerous}
                onChange={(e) => setForm({ ...form, isDangerous: e.target.checked })}
              />
              <label htmlFor="dangerous">Marchandises dangereuses</label>
            </div>

            <div className="card-title" style={{ marginTop: 20 }}>
              <h4>📦 Articles</h4>
              <button
                type="button"
                className="btn btn-outline btn-sm"
                onClick={() => setShowItemsForm(!showItemsForm)}
              >
                {showItemsForm ? 'Fermer' : '+ Ajouter un article'}
              </button>
            </div>
            {showItemsForm && (
              <div className="data-grid" style={{ marginBottom: 10 }}>
                <input
                  placeholder="SKU"
                  value={newItem.sku}
                  onChange={(e) => setNewItem({ ...newItem, sku: e.target.value })}
                />
                <input
                  placeholder="Nom"
                  value={newItem.name}
                  onChange={(e) => setNewItem({ ...newItem, name: e.target.value })}
                />
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="Qté"
                  value={newItem.quantity}
                  onChange={(e) =>
                    setNewItem({ ...newItem, quantity: parseFloat(e.target.value) || 1 })
                  }
                />
                <select
                  value={newItem.unit}
                  onChange={(e) => setNewItem({ ...newItem, unit: e.target.value })}
                >
                  <option value="PCS">PCS</option>
                  <option value="KG">KG</option>
                  <option value="L">L</option>
                  <option value="M">M</option>
                  <option value="M2">M2</option>
                  <option value="CRT">CRT</option>
                </select>
                <button type="button" className="btn btn-primary btn-sm" onClick={addItem}>
                  + Ligne
                </button>
              </div>
            )}
            {form.items.length > 0 && (
              <div style={{ marginBottom: 14 }}>
                {form.items.map((item, idx) => (
                  <div key={idx} className="record-card-row">
                    <span>
                      <strong>{item.sku || '—'}</strong> {item.name}
                    </span>
                    <span className="muted">
                      {item.quantity} {item.unit}{' '}
                      <button
                        type="button"
                        onClick={() => removeItem(idx)}
                        style={{
                          background: 'none',
                          border: 'none',
                          cursor: 'pointer',
                          color: 'var(--red)'
                        }}
                      >
                        ✕
                      </button>
                    </span>
                  </div>
                ))}
              </div>
            )}

            {form.shipperCountry && form.consigneeCountry && (
              <div style={{ marginTop: 10 }}>
                <div className="card-title">
                  <h4>⚖️ Tarif négocié (optionnel)</h4>
                  <button
                    type="button"
                    className="btn btn-outline btn-sm"
                    onClick={() => setShowRateSearch(!showRateSearch)}
                  >
                    {showRateSearch ? 'Fermer' : 'Rechercher un tarif'}
                  </button>
                </div>
                {showRateSearch && (
                  <div>
                    {matchingRates.length > 0 ? (
                      <div className="couple-cards">
                        {matchingRates.map((rate) => {
                          const isSelected = form.shippingRateId === rate.id
                          const estimatedCost =
                            rate.baseRate + rate.ratePerKg * (form.weightKg || 0)
                          return (
                            <button
                              key={rate.id}
                              type="button"
                              className={`couple-card ${isSelected ? 'best-rate' : ''}`}
                              style={{ textAlign: 'left', cursor: 'pointer' }}
                              onClick={() =>
                                setForm({
                                  ...form,
                                  shippingRateId: rate.id,
                                  carrierId: rate.carrierId || ''
                                })
                              }
                            >
                              <div className="couple-card-top">
                                <strong>{rate.carrierName || rate.name}</strong>
                                <span className="score-pill good">
                                  {estimatedCost.toFixed(2)} €
                                </span>
                              </div>
                              <div className="muted">
                                {rate.transportMode === 'SEA'
                                  ? 'Maritime'
                                  : rate.transportMode === 'AIR'
                                    ? 'Aérien'
                                    : 'Routier'}
                                {rate.transitDaysMin &&
                                  rate.transitDaysMax &&
                                  ` · ${rate.transitDaysMin}-${rate.transitDaysMax}j`}
                                {rate.ratePerKg > 0 && ` · ${rate.ratePerKg}€/kg`}
                              </div>
                              {isSelected && (
                                <div className="text-success">✓ Tarif sélectionné</div>
                              )}
                            </button>
                          )
                        })}
                      </div>
                    ) : (
                      <p className="muted">
                        Aucun tarif trouvé pour cette route. Ajoutez des tarifs dans Gestion tarifs
                        ou sélectionnez un transporteur manuellement.
                      </p>
                    )}
                  </div>
                )}
              </div>
            )}

            {carriers.length > 0 && (
              <div style={{ marginTop: 20 }}>
                <h4>🚛 Transporteur (optionnel)</h4>
                <div className="data-grid">
                  {carriers.map((c) => (
                    <label
                      key={c.id}
                      className={`form-field ${form.carrierId === c.id ? 'selected' : ''}`}
                      style={{
                        border: `1px solid ${form.carrierId === c.id ? 'var(--primary)' : 'var(--border)'}`,
                        padding: 10,
                        cursor: 'pointer'
                      }}
                    >
                      <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <input
                          type="radio"
                          name="carrierId"
                          checked={form.carrierId === c.id}
                          onChange={() => setForm({ ...form, carrierId: c.id })}
                        />
                        <span>
                          <strong style={{ textTransform: 'none', fontSize: 13 }}>{c.name}</strong>
                          <span className="muted block">{c.transportModes}</span>
                        </span>
                      </span>
                    </label>
                  ))}
                </div>
              </div>
            )}

            {fleetVehicles.length > 0 && (
              <div className="form-field" style={{ marginTop: 20 }}>
                <label>🚛 Camion de la flotte propre (optionnel)</label>
                <p className="muted" style={{ marginBottom: 6 }}>
                  À renseigner si cette expédition est livrée par votre flotte plutôt que par un
                  transporteur tiers.
                </p>
                <select
                  value={form.fleetHubTruckRegistration}
                  onChange={(e) => setForm({ ...form, fleetHubTruckRegistration: e.target.value })}
                >
                  <option value="">Aucun camion assigné</option>
                  {fleetVehicles.map((v) => (
                    <option key={v.truckId} value={v.registration}>
                      {v.registration}
                      {v.driverName ? ` — ${v.driverName}` : ''}
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Création…' : "Créer l'expédition"}
              </button>
              <button type="button" className="btn btn-outline" onClick={() => setShowForm(false)}>
                Annuler
              </button>
            </div>
          </form>
        </div>
      )}

      {loading && (
        <div className="loading-spinner" aria-label="Chargement des expéditions…">
          Chargement…
        </div>
      )}

      {shipments.length === 0 && !loading ? (
        <div className="card" style={{ textAlign: 'center', padding: 40 }}>
          <p className="muted">Créez votre première expédition pour commencer</p>
          <button className="btn btn-primary" onClick={openCreate}>
            + Nouvelle expédition
          </button>
        </div>
      ) : (
        <div className="card desktop-table">
          <div className="table-scroll">
            <table className="table table-hover">
              <thead>
                <tr>
                  <th>N° commande</th>
                  <th>Statut</th>
                  <th>Itinéraire</th>
                  <th>Transporteur</th>
                  <th>Coût</th>
                  <th>Date</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {shipments.map((shipment) => {
                  const status = STATUS_CONFIG[shipment.status] || STATUS_CONFIG.DRAFT
                  const isExpanded = expandedId === shipment.id
                  const mode = shipment.carrierName?.includes('Air')
                    ? 'AIR'
                    : shipment.carrierName?.includes('Sea') ||
                        shipment.carrierName?.includes('Maritime')
                      ? 'MARITIME'
                      : 'ROAD'
                  return (
                    <Fragment key={shipment.id}>
                      <tr
                        className="row-clickable"
                        onClick={() => setExpandedId(isExpanded ? null : shipment.id)}
                      >
                        <td className="cell-strong" style={{ fontFamily: 'var(--font-mono)' }}>
                          {shipment.orderNumber}
                        </td>
                        <td>
                          <span className={`badge badge-${status.color}`}>{status.label}</span>
                        </td>
                        <td>
                          {shipment.shipperCity || '—'} <span className="muted">→</span>{' '}
                          {shipment.consigneeCity || '—'}
                        </td>
                        <td className="muted">{shipment.carrierName || '—'}</td>
                        <td>
                          {shipment.finalCost != null
                            ? `${shipment.finalCost.toFixed(2)} €`
                            : shipment.quotedCost != null
                              ? `${shipment.quotedCost.toFixed(2)} €`
                              : '—'}
                        </td>
                        <td className="muted">
                          {new Date(shipment.createdAt).toLocaleDateString('fr-FR')}
                        </td>
                        <td onClick={(e) => e.stopPropagation()}>
                          <div className="row-actions">
                            {shipment.status === 'DRAFT' && (
                              <button
                                className="btn btn-outline btn-sm"
                                disabled={busyId === shipment.id}
                                onClick={() => updateStatus(shipment.id, 'BOOKED')}
                              >
                                Réserver
                              </button>
                            )}
                            {shipment.status === 'BOOKED' && (
                              <button
                                className="btn btn-outline btn-sm"
                                disabled={busyId === shipment.id}
                                onClick={() => updateStatus(shipment.id, 'IN_TRANSIT')}
                              >
                                En transit
                              </button>
                            )}
                            {shipment.status === 'IN_TRANSIT' && (
                              <button
                                className="btn btn-outline btn-sm"
                                disabled={busyId === shipment.id}
                                onClick={() => updateStatus(shipment.id, 'DELIVERED')}
                              >
                                Livré
                              </button>
                            )}
                            <button
                              className="btn btn-danger btn-sm"
                              disabled={busyId === shipment.id}
                              onClick={() => remove(shipment.id)}
                            >
                              Supprimer
                            </button>
                          </div>
                        </td>
                      </tr>
                      {isExpanded && (
                        <tr>
                          <td colSpan="7">
                            <div className="dashboard-grid">
                              <div>
                                <h4>Détails</h4>
                                <div className="kpi-grid">
                                  <div className="kpi-item">
                                    <span>Expéditeur</span>
                                    <b>
                                      {shipment.shipperName} — {shipment.shipperCity}
                                    </b>
                                  </div>
                                  <div className="kpi-item">
                                    <span>Destinataire</span>
                                    <b>
                                      {shipment.consigneeName} — {shipment.consigneeCity}
                                    </b>
                                  </div>
                                  <div className="kpi-item">
                                    <span>Marchandises</span>
                                    <b>{shipment.goodsDescription || '—'}</b>
                                  </div>
                                  <div className="kpi-item">
                                    <span>Poids / Volume</span>
                                    <b>
                                      {shipment.weightKg || '—'} kg / {shipment.volumeM3 || '—'} m³
                                    </b>
                                  </div>
                                  <div className="kpi-item">
                                    <span>Incoterm</span>
                                    <b>{shipment.incotermCode || '—'}</b>
                                  </div>
                                </div>

                                <h4 style={{ marginTop: 16 }}>📄 Documents</h4>
                                <div className="chip-row">
                                  <button
                                    className="btn btn-outline btn-sm"
                                    disabled={exportingId === `${shipment.id}-cmr`}
                                    onClick={() =>
                                      exportDoc(shipment.id, shipment.orderNumber, 'cmr', 'cmr')
                                    }
                                  >
                                    CMR
                                  </button>
                                  <button
                                    className="btn btn-outline btn-sm"
                                    disabled={exportingId === `${shipment.id}-dgd`}
                                    onClick={() =>
                                      exportDoc(shipment.id, shipment.orderNumber, 'dgd', 'dgd')
                                    }
                                  >
                                    DGD
                                  </button>
                                  <button
                                    className="btn btn-outline btn-sm"
                                    disabled={
                                      exportingId === `${shipment.id}-certificate-of-origin`
                                    }
                                    onClick={() =>
                                      exportDoc(
                                        shipment.id,
                                        shipment.orderNumber,
                                        'certificate-of-origin',
                                        'certificat-origine'
                                      )
                                    }
                                  >
                                    Certificat d'origine
                                  </button>
                                  <button
                                    className="btn btn-outline btn-sm"
                                    disabled={exportingId === `${shipment.id}-pdf`}
                                    onClick={() =>
                                      exportDoc(
                                        shipment.id,
                                        shipment.orderNumber,
                                        'pdf',
                                        'etiquette'
                                      )
                                    }
                                  >
                                    Étiquette PDF
                                  </button>
                                </div>

                                <h4 style={{ marginTop: 16 }}>🔗 Portail client</h4>
                                <button
                                  className="btn btn-outline btn-sm"
                                  onClick={() => generateShareLink(shipment.id)}
                                >
                                  Générer un lien de suivi client
                                </button>
                                {clientLinks[shipment.id] && (
                                  <div className="copy-row" style={{ marginTop: 8 }}>
                                    <input
                                      type="text"
                                      readOnly
                                      value={clientLinks[shipment.id]}
                                      style={{ flex: 1, fontSize: 12 }}
                                    />
                                    <button
                                      className="btn btn-outline btn-sm"
                                      onClick={() => copyLink(shipment.id)}
                                    >
                                      {copiedLinkId === shipment.id ? '✓ Copié' : 'Copier'}
                                    </button>
                                    <a
                                      href={clientLinks[shipment.id]}
                                      target="_blank"
                                      rel="noopener noreferrer"
                                      className="btn btn-outline btn-sm"
                                    >
                                      Voir ↗
                                    </a>
                                  </div>
                                )}

                                <ShipmentReceivingSection shipmentId={shipment.id} />
                              </div>

                              <div>
                                <h4>Suivi</h4>
                                <LiveTrackingPanel shipmentId={shipment.id} mode={mode} />
                                <h4 style={{ marginTop: 16 }}>Événements de suivi</h4>
                                <TrackingTimeline events={shipment.trackingEvents || []} />
                              </div>
                            </div>

                            {shipment.items && shipment.items.length > 0 && (
                              <div
                                style={{
                                  marginTop: 16,
                                  paddingTop: 16,
                                  borderTop: '1px dashed var(--border)'
                                }}
                              >
                                <h4>
                                  📦 Articles (
                                  {shipment.items.reduce((s, it) => s + (it.quantity ?? 1), 0)})
                                </h4>
                                {shipment.items.map((item, idx) => (
                                  <div key={idx} className="record-card-row">
                                    <span style={{ fontFamily: 'var(--font-mono)' }}>
                                      {item.sku || '—'}
                                    </span>
                                    <span className="muted">
                                      {item.name} · {item.quantity} {item.unit}
                                    </span>
                                  </div>
                                ))}
                              </div>
                            )}
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}
