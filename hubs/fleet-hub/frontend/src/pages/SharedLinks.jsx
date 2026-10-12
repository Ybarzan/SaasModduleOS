import { useEffect, useState } from 'react'
import api from '../services/api'
import StatCard from '../components/StatCard'

export default function SharedLinks() {
  const [links, setLinks] = useState([])
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ shipmentId: '', label: '', expiresHours: '' })
  const [submitting, setSubmitting] = useState(false)

  const load = () => {
    setLoading(true)
    api
      .get('/shared')
      .then((res) => setLinks(res.data || []))
      .catch(() => setError('Impossible de charger les liens'))
      .finally(() => setLoading(false))
    api
      .get('/shared/stats')
      .then((res) => setStats(res.data))
      .catch(() => {})
  }

  useEffect(() => {
    load()
  }, [])

  const copyLink = (url) => {
    const full = `${window.location.origin}${url}`
    navigator.clipboard?.writeText(full)
    setNotice('Lien copié !')
  }

  const handleCreate = async (e) => {
    e.preventDefault()
    setError('')
    setNotice('')
    if (!form.shipmentId.trim()) {
      setError("ID d'expédition requis")
      return
    }
    setSubmitting(true)
    try {
      const res = await api.post('/shared', {
        shipmentId: form.shipmentId.trim(),
        label: form.label.trim() || undefined,
        expiresHours: form.expiresHours ? parseInt(form.expiresHours) : undefined
      })
      setShowForm(false)
      setForm({ shipmentId: '', label: '', expiresHours: '' })
      load()
      copyLink(res.data.url)
      setNotice('Lien créé et copié !')
    } catch (err) {
      setError(err.response?.data?.message || 'Erreur lors de la création')
    } finally {
      setSubmitting(false)
    }
  }

  const revoke = async (id) => {
    if (!window.confirm('Révoquer ce lien de suivi ?')) return
    try {
      await api.delete(`/shared/${id}`)
      setNotice('Lien révoqué')
      load()
    } catch {
      setError('La révocation a échoué')
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Liens de suivi</h2>
          <p>Créez des liens partagés pour que vos clients suivent leurs expéditions</p>
        </div>
        <div className="header-actions">
          <button className="btn btn-primary" onClick={() => setShowForm(true)}>
            + Nouveau lien
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      {stats && (
        <div className="north-star-grid">
          <StatCard label="Total liens" value={stats.totalLinks} icon="🔗" tone="blue" />
          <StatCard label="Actifs" value={stats.activeLinks} icon="✅" tone="green" />
          <StatCard label="Consultations" value={stats.totalAccesses} icon="👁️" tone="purple" />
        </div>
      )}

      {showForm && (
        <div className="card">
          <div className="card-title">
            <h3>Nouveau lien de suivi</h3>
          </div>
          <form onSubmit={handleCreate}>
            <div className="data-grid">
              <div className="form-field">
                <label>ID de l'expédition (UUID) *</label>
                <input
                  type="text"
                  value={form.shipmentId}
                  required
                  onChange={(e) => setForm({ ...form, shipmentId: e.target.value })}
                  style={{ fontFamily: 'var(--font-mono)' }}
                />
              </div>
              <div className="form-field">
                <label>Label (optionnel)</label>
                <input
                  type="text"
                  value={form.label}
                  onChange={(e) => setForm({ ...form, label: e.target.value })}
                />
              </div>
              <div className="form-field">
                <label>Expiration en heures (optionnel)</label>
                <input
                  type="number"
                  value={form.expiresHours}
                  onChange={(e) => setForm({ ...form, expiresHours: e.target.value })}
                  placeholder="vide = pas d'expiration"
                />
              </div>
            </div>
            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Création…' : 'Créer le lien'}
              </button>
              <button type="button" className="btn btn-outline" onClick={() => setShowForm(false)}>
                Annuler
              </button>
            </div>
          </form>
        </div>
      )}

      {loading && (
        <div className="loading-spinner" aria-label="Chargement des liens…">
          Chargement…
        </div>
      )}

      <div className="card desktop-table">
        <div className="table-scroll">
          <table className="table table-hover">
            <thead>
              <tr>
                <th>Label</th>
                <th>Expédition</th>
                <th>Accès</th>
                <th>Expiration</th>
                <th>Statut</th>
                <th>Créé le</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {links.map((link) => (
                <tr key={link.id}>
                  <td className="cell-strong">{link.label || '—'}</td>
                  <td className="muted" style={{ fontFamily: 'var(--font-mono)' }}>
                    {link.orderNumber}
                  </td>
                  <td>👁️ {link.accessCount}</td>
                  <td className="muted">
                    {link.expiresAt
                      ? new Date(link.expiresAt).toLocaleDateString('fr-FR')
                      : 'Jamais'}
                  </td>
                  <td>
                    <span className={`badge ${link.active ? 'badge-green' : 'badge-red'}`}>
                      {link.active ? 'Actif' : 'Révoqué'}
                    </span>
                  </td>
                  <td className="muted">{new Date(link.createdAt).toLocaleDateString('fr-FR')}</td>
                  <td>
                    {link.active && (
                      <div className="row-actions">
                        <button
                          className="btn btn-outline btn-sm"
                          onClick={() => copyLink(link.url)}
                        >
                          Copier
                        </button>
                        <a
                          href={link.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="btn btn-outline btn-sm"
                        >
                          Ouvrir
                        </a>
                        <button className="btn btn-danger btn-sm" onClick={() => revoke(link.id)}>
                          Révoquer
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
              {links.length === 0 && !loading && (
                <tr>
                  <td colSpan="7" className="table-empty">
                    Aucun lien partagé
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
