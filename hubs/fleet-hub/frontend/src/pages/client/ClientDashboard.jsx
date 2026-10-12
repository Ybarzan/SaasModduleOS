import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
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

export default function ClientDashboard() {
  const navigate = useNavigate()
  const [client, setClient] = useState(null)
  const [shipments, setShipments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    try {
      setClient(JSON.parse(localStorage.getItem('fh_client_user') || 'null'))
    } catch {
      setClient(null)
    }
  }, [])

  const load = () => {
    setLoading(true)
    api
      .get('/client/shipments')
      .then((res) => setShipments(res.data || []))
      .catch(() => setError('Impossible de charger vos expéditions'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    const id = setInterval(load, 30000)
    return () => clearInterval(id)
  }, [])

  const logout = () => {
    localStorage.removeItem('fh_client_token')
    localStorage.removeItem('fh_client_user')
    navigate('/client/login')
  }

  const total = shipments.length
  const inTransit = shipments.filter((s) => s.status === 'IN_TRANSIT').length
  const delivered = shipments.filter((s) => s.status === 'DELIVERED').length

  return (
    <div className="cp-page">
      <header className="cp-header">
        <div className="cp-header-inner">
          <div className="cp-brand">
            <span className="cp-brand-icon">📦</span>
            <div>
              <strong>Mon suivi</strong>
              <span className="muted block">{client?.fullName || client?.email}</span>
            </div>
          </div>
          <button className="btn btn-outline btn-sm" onClick={logout}>
            Déconnexion
          </button>
        </div>
      </header>

      <div className="cp-container">
        {error && <div className="alert alert-error">{error}</div>}

        <div className="north-star-grid">
          <div className="stat-card blue">
            <div className="stat-icon">📦</div>
            <div className="stat-body">
              <div className="stat-label">Total expéditions</div>
              <div className="stat-value">{total}</div>
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
        </div>

        <div className="page-header" style={{ margin: 0 }}>
          <h3>Vos expéditions</h3>
          <button className="btn btn-outline btn-sm" onClick={load}>
            🔄 Actualiser
          </button>
        </div>

        {loading ? (
          <div className="loading-spinner" aria-label="Chargement…">
            Chargement…
          </div>
        ) : shipments.length === 0 ? (
          <div className="card" style={{ textAlign: 'center', padding: 40 }}>
            <p className="muted">Aucune expédition partagée pour le moment</p>
          </div>
        ) : (
          shipments.map((s) => (
            <Link key={s.id} to={`/client/shipments/${s.id}`} className="cp-shipment-row">
              <div className="couple-card-top">
                <div>
                  <strong style={{ fontFamily: 'var(--font-mono)' }}>{s.orderNumber}</strong>
                  <span
                    className={`badge badge-${STATUS_COLORS[s.status] || 'gray'}`}
                    style={{ marginLeft: 8 }}
                  >
                    {STATUS_LABELS[s.status] || s.status}
                  </span>
                </div>
                {s.estimatedDeliveryDate && (
                  <span className="muted">
                    {new Date(s.estimatedDeliveryDate).toLocaleDateString('fr-FR')}
                  </span>
                )}
              </div>
              <div className="muted">
                {s.shipperCity || '—'}
                {s.shipperCountry ? `, ${s.shipperCountry}` : ''} → {s.consigneeCity || '—'}
                {s.consigneeCountry ? `, ${s.consigneeCountry}` : ''}
              </div>
              {s.carrierName && <div className="muted">{s.carrierName}</div>}
            </Link>
          ))
        )}
      </div>
    </div>
  )
}
