import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
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

export default function SharedTracking() {
  const { token } = useParams()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!token) return
    api
      .get(`/shared/access/${token}`)
      .then((res) => setData(res.data))
      .catch((err) => setError(err.response?.data?.message || 'Lien invalide ou expiré'))
      .finally(() => setLoading(false))
  }, [token])

  if (loading) {
    return (
      <div
        className="cp-page"
        style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}
      >
        <span className="spinner" />
      </div>
    )
  }

  if (error || !data) {
    return (
      <div
        className="cp-page"
        style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24 }}
      >
        <div style={{ textAlign: 'center' }}>
          <h3>Lien invalide</h3>
          <p className="muted">{error || "Ce lien de suivi n'est plus disponible"}</p>
        </div>
      </div>
    )
  }

  const { shipment, trackingEvents = [], companyName, companyLogo, label } = data

  return (
    <div className="cp-page">
      <header className="cp-header">
        <div className="cp-header-inner">
          <div className="cp-brand">
            {companyLogo ? (
              <img
                src={companyLogo}
                alt={companyName}
                style={{ width: 38, height: 38, objectFit: 'cover' }}
              />
            ) : (
              <span className="cp-brand-icon">📦</span>
            )}
            <div>
              <strong>{companyName}</strong>
              {label && <span className="muted block">{label}</span>}
            </div>
          </div>
          <span className="muted">Fleet Hub</span>
        </div>
      </header>

      <div className="cp-container">
        <div className="card">
          <div style={{ marginBottom: 14 }}>
            <div className="muted">Statut</div>
            <span
              className={`badge badge-${STATUS_COLORS[shipment.status] || 'gray'}`}
              style={{ fontSize: 14, marginTop: 4 }}
            >
              {STATUS_LABELS[shipment.status] || shipment.status}
            </span>
          </div>
          <div className="cp-route">
            <div className="cp-route-point">
              <div className="muted">Origine</div>
              <strong>{shipment.shipperCity || '—'}</strong>
              <div className="muted">{shipment.shipperCountry}</div>
            </div>
            <div className="cp-route-arrow">→</div>
            <div className="cp-route-point end">
              <div className="muted">Destination</div>
              <strong>{shipment.consigneeCity || '—'}</strong>
              <div className="muted">{shipment.consigneeCountry}</div>
            </div>
          </div>
          <div className="truck-card-grid" style={{ marginTop: 14 }}>
            {shipment.carrierName && (
              <div className="couple-stat">
                <span>Transporteur</span>
                <b>{shipment.carrierName}</b>
              </div>
            )}
            {shipment.weightKg && (
              <div className="couple-stat">
                <span>Poids</span>
                <b>{shipment.weightKg} kg</b>
              </div>
            )}
            {shipment.estimatedDeliveryDate && (
              <div className="couple-stat">
                <span>Livraison est.</span>
                <b>{new Date(shipment.estimatedDeliveryDate).toLocaleDateString('fr-FR')}</b>
              </div>
            )}
          </div>
        </div>

        <div className="card">
          <div className="card-title">
            <h3>Historique de suivi</h3>
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

        <p className="muted" style={{ textAlign: 'center' }}>
          Suivi propulsé par <strong>Fleet Hub</strong>
        </p>
      </div>
    </div>
  )
}
