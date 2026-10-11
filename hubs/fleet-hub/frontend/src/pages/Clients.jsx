import { useEffect, useState } from 'react'
import api from '../services/api'

const EMPTY_FORM = { email: '', password: '', fullName: '', phone: '' }

export default function Clients() {
  const [clients, setClients] = useState([])
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [resetTarget, setResetTarget] = useState(null)
  const [newPw, setNewPw] = useState('')
  const [busyId, setBusyId] = useState(null)

  const load = () => {
    setLoading(true)
    api
      .get('/clients')
      .then((res) => setClients(res.data || []))
      .catch(() => setError('Impossible de charger les clients'))
      .finally(() => setLoading(false))
    api
      .get('/clients/stats')
      .then((res) => setStats(res.data))
      .catch(() => {})
  }

  useEffect(() => {
    load()
  }, [])

  const handleCreate = async (e) => {
    e.preventDefault()
    setError('')
    setNotice('')
    setSubmitting(true)
    try {
      await api.post('/clients', form)
      setNotice('Client créé')
      setShowForm(false)
      setForm(EMPTY_FORM)
      load()
    } catch (err) {
      setError(err.response?.data?.message || 'Erreur lors de la création')
    } finally {
      setSubmitting(false)
    }
  }

  const toggleActive = async (c) => {
    setBusyId(c.id)
    try {
      await api.put(`/clients/${c.id}`, { active: !c.active })
      setNotice('Client mis à jour')
      load()
    } catch {
      setError('La mise à jour a échoué')
    } finally {
      setBusyId(null)
    }
  }

  const remove = async (c) => {
    if (!window.confirm(`Supprimer définitivement le compte client ${c.fullName || c.email} ?`)) return
    setBusyId(c.id)
    try {
      await api.delete(`/clients/${c.id}`)
      setNotice('Client supprimé')
      load()
    } catch {
      setError('La suppression a échoué')
    } finally {
      setBusyId(null)
    }
  }

  const resetPassword = async () => {
    if (newPw.length < 8) return
    setSubmitting(true)
    try {
      await api.post(`/clients/${resetTarget}/reset-password`, { newPassword: newPw })
      setNotice('Mot de passe réinitialisé')
      setResetTarget(null)
      setNewPw('')
    } catch (err) {
      setError(err.response?.data?.message || 'Erreur lors de la réinitialisation')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Clients</h2>
          <p>Comptes clients qui accèdent au portail de suivi</p>
        </div>
        <div className="header-actions">
          <button className="btn btn-primary" onClick={() => setShowForm(true)}>
            + Nouveau client
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      {stats && (
        <div className="north-star-grid">
          <div className="stat-card blue">
            <div className="stat-icon">👥</div>
            <div className="stat-body">
              <div className="stat-label">Total clients</div>
              <div className="stat-value">{stats.totalClients}</div>
            </div>
          </div>
          <div className="stat-card green">
            <div className="stat-icon">✅</div>
            <div className="stat-body">
              <div className="stat-label">Actifs</div>
              <div className="stat-value">{stats.activeClients}</div>
            </div>
          </div>
        </div>
      )}

      {showForm && (
        <div className="card">
          <div className="card-title">
            <h3>Nouveau client</h3>
          </div>
          <form onSubmit={handleCreate}>
            <div className="data-grid">
              <div className="form-field">
                <label>Nom complet *</label>
                <input type="text" value={form.fullName} required onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
              </div>
              <div className="form-field">
                <label>Email *</label>
                <input type="email" value={form.email} required onChange={(e) => setForm({ ...form, email: e.target.value })} />
              </div>
              <div className="form-field">
                <label>Mot de passe (min. 8 car.) *</label>
                <input type="password" value={form.password} required minLength={8} onChange={(e) => setForm({ ...form, password: e.target.value })} />
              </div>
              <div className="form-field">
                <label>Téléphone</label>
                <input type="text" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
              </div>
            </div>
            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Création…' : 'Créer le client'}
              </button>
              <button type="button" className="btn btn-outline" onClick={() => setShowForm(false)}>
                Annuler
              </button>
            </div>
          </form>
        </div>
      )}

      {resetTarget && (
        <div className="card">
          <div className="card-title">
            <h3>Nouveau mot de passe</h3>
          </div>
          <div className="form-field">
            <input
              type="password"
              placeholder="Nouveau mot de passe (min. 8 car.)"
              value={newPw}
              onChange={(e) => setNewPw(e.target.value)}
            />
          </div>
          <div className="form-actions">
            <button className="btn btn-primary" disabled={newPw.length < 8 || submitting} onClick={resetPassword}>
              Réinitialiser
            </button>
            <button
              className="btn btn-outline"
              onClick={() => {
                setResetTarget(null)
                setNewPw('')
              }}
            >
              Annuler
            </button>
          </div>
        </div>
      )}

      {loading && (
        <div className="loading-spinner" aria-label="Chargement des clients…">
          Chargement…
        </div>
      )}

      <div className="card desktop-table">
        <div className="table-scroll">
          <table className="table table-hover">
            <thead>
              <tr>
                <th>Client</th>
                <th>Email</th>
                <th>Téléphone</th>
                <th>Statut</th>
                <th>Créé le</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {clients.map((c) => (
                <tr key={c.id}>
                  <td className="cell-strong">{c.fullName || '—'}</td>
                  <td className="muted">{c.email}</td>
                  <td className="muted">{c.phone || '—'}</td>
                  <td>
                    <span className={`badge ${c.active ? 'badge-green' : 'badge-red'}`}>{c.active ? 'Actif' : 'Inactif'}</span>
                  </td>
                  <td className="muted">{new Date(c.createdAt).toLocaleDateString('fr-FR')}</td>
                  <td>
                    <div className="row-actions">
                      <button className="btn btn-outline btn-sm" disabled={busyId === c.id} onClick={() => toggleActive(c)}>
                        {c.active ? 'Désactiver' : 'Activer'}
                      </button>
                      <button className="btn btn-outline btn-sm" onClick={() => setResetTarget(c.id)}>
                        Mot de passe
                      </button>
                      <button className="btn btn-danger btn-sm" disabled={busyId === c.id} onClick={() => remove(c)}>
                        Supprimer
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {clients.length === 0 && !loading && (
                <tr>
                  <td colSpan="6" className="table-empty">
                    Aucun client
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
