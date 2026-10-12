import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import api from '../../services/api'
import './client.css'

const STATUS_LABELS = {
  DRAFT: 'Brouillon',
  QUOTED: 'Devisé',
  BOOKED: 'Réservé',
  IN_TRANSIT: 'En transit',
  DELIVERED: 'Livré',
  CANCELLED: 'Annulé'
}

const STATUS_COLORS = {
  DRAFT: 'gray',
  QUOTED: 'blue',
  BOOKED: 'blue',
  IN_TRANSIT: 'orange',
  DELIVERED: 'green',
  CANCELLED: 'red'
}

const DOCUMENTS = [
  { type: 'label', label: "Étiquette d'expédition" },
  { type: 'cmr', label: 'CMR' },
  { type: 'certificate-of-origin', label: "Certificat d'origine" }
]

async function downloadDoc(id, type, orderNumber) {
  const res = await api.get(`/client/shipments/${id}/documents/${type}`, { responseType: 'blob' })
  const url = window.URL.createObjectURL(new Blob([res.data]))
  const a = document.createElement('a')
  a.href = url
  a.download = `${type}-${orderNumber}.pdf`
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.URL.revokeObjectURL(url)
}

export default function ClientShipmentDetail() {
  const { id } = useParams()
  const [shipment, setShipment] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const load = () =>
      api
        .get(`/client/shipments/${id}`)
        .then((res) => setShipment(res.data))
        .catch(() => setShipment(null))
        .finally(() => setLoading(false))
    load()
    const interval = setInterval(load, 30000)
    return () => clearInterval(interval)
  }, [id])

  if (loading) {
    return (
      <div
        className="cp-page"
        style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}
      >
        <span className="muted">Chargement…</span>
      </div>
    )
  }

  if (!shipment) {
    return (
      <div
        className="cp-page"
        style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}
      >
        <span className="muted">Expédition introuvable</span>
      </div>
    )
  }

  const trackingEvents = shipment.trackingEvents || []
  const displayCost = shipment.finalCost || shipment.quotedCost

  return (
    <div className="cp-page">
      <header className="cp-header">
        <div className="cp-header-inner">
          <div>
            <Link to="/client/dashboard" className="link">
              ← Retour au tableau de bord
            </Link>
            <h3 style={{ fontFamily: 'var(--font-mono)', marginTop: 6 }}>{shipment.orderNumber}</h3>
            <div style={{ marginTop: 4 }}>
              <span className={`badge badge-${STATUS_COLORS[shipment.status] || 'gray'}`}>
                {STATUS_LABELS[shipment.status] || shipment.status}
              </span>
              {shipment.incotermCode && (
                <span className="badge badge-gray" style={{ marginLeft: 6 }}>
                  {shipment.incotermCode}
                </span>
              )}
            </div>
          </div>
          {shipment.carrierName && (
            <div style={{ textAlign: 'right' }}>
              <strong>{shipment.carrierName}</strong>
              <span className="muted block">Transporteur</span>
            </div>
          )}
        </div>
      </header>

      <div className="cp-container">
        <div className="card">
          <div className="card-title">
            <h3>Itinéraire</h3>
          </div>
          <div className="cp-route">
            <div className="cp-route-point">
              <div className="muted">Origine</div>
              <strong>{shipment.shipperCity || '—'}</strong>
              {shipment.shipperCountry && <div className="muted">{shipment.shipperCountry}</div>}
            </div>
            <div className="cp-route-arrow">→</div>
            <div className="cp-route-point end">
              <div className="muted">Destination</div>
              <strong>{shipment.consigneeCity || '—'}</strong>
              {shipment.consigneeCountry && (
                <div className="muted">{shipment.consigneeCountry}</div>
              )}
            </div>
          </div>
        </div>

        {displayCost && (
          <div className="card">
            <div className="card-title">
              <h3>Coût</h3>
            </div>
            <div className="stat-card green" style={{ maxWidth: 280 }}>
              <div className="stat-icon">💰</div>
              <div className="stat-body">
                <div className="stat-label">Coût total</div>
                <div className="stat-value">
                  {displayCost.toLocaleString('fr-FR')} {shipment.costCurrency || 'EUR'}
                </div>
              </div>
            </div>
          </div>
        )}

        <div className="card">
          <div className="card-title">
            <h3>Marchandise</h3>
          </div>
          {shipment.goodsDescription && <p>{shipment.goodsDescription}</p>}
          <div className="truck-card-grid">
            <div className="couple-stat">
              <span>Poids</span>
              <b>{shipment.weightKg ? `${shipment.weightKg} kg` : '—'}</b>
            </div>
            <div className="couple-stat">
              <span>Volume</span>
              <b>{shipment.volumeM3 ? `${shipment.volumeM3} m³` : '—'}</b>
            </div>
            <div className="couple-stat">
              <span>Colis</span>
              <b>{shipment.packagesCount || '—'}</b>
            </div>
            <div className="couple-stat">
              <span>Livraison est.</span>
              <b>
                {shipment.estimatedDeliveryDate
                  ? new Date(shipment.estimatedDeliveryDate).toLocaleDateString('fr-FR')
                  : '—'}
              </b>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-title">
            <h3>Documents</h3>
          </div>
          <div className="chip-row">
            {DOCUMENTS.map((doc) => (
              <button
                key={doc.type}
                className="btn btn-outline btn-sm"
                onClick={() => downloadDoc(id, doc.type, shipment.orderNumber).catch(() => {})}
              >
                ⬇ {doc.label}
              </button>
            ))}
          </div>
        </div>

        <div className="card">
          <div className="card-title">
            <h3>Suivi en temps réel</h3>
          </div>
          {trackingEvents.length === 0 ? (
            <p className="muted table-empty">Aucun événement de suivi disponible</p>
          ) : (
            <div className="timeline">
              {trackingEvents.map((event, idx) => (
                <div key={event.id || idx} className="timeline-item">
                  <span className={`timeline-dot ${idx === 0 ? 'done' : ''}`} />
                  <div className="timeline-body">
                    <span className="badge badge-blue">{event.status}</span>
                    {event.source && <span className="muted"> · via {event.source}</span>}
                    {event.dataSource === 'MANUAL' && (
                      <span className="badge badge-gray" style={{ marginLeft: 6 }}>
                        Manuel
                      </span>
                    )}
                    {event.description && <p className="muted">{event.description}</p>}
                    {event.location && <div className="muted">📍 {event.location}</div>}
                    <span className="timeline-time">
                      {new Date(event.eventTime).toLocaleString('fr-FR')}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
