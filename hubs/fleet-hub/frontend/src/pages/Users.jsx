import { useEffect, useState } from 'react'
import api from '../services/api'
import { useAuth } from '../context/AuthContext'

const ROLE_LABEL = {
  ADMIN: 'Administrateur',
  GESTIONNAIRE: 'Gestionnaire'
}

const fmtDate = (s) => (s ? new Date(s).toLocaleDateString('fr-FR') : '—')

const buildInviteUrl = (inviteUrl) => {
  try {
    const token = new URL(inviteUrl).searchParams.get('token')
    if (token) return `${window.location.origin}/accept-invitation?token=${token}`
  } catch {
    /* URL serveur illisible : on garde la valeur retournée */
  }
  return inviteUrl || ''
}

export default function Users() {
  const { user: me } = useAuth()
  const [users, setUsers] = useState([])
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState(null)
  const [inviteUrl, setInviteUrl] = useState('')
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', role: 'GESTIONNAIRE' })
  const [submitting, setSubmitting] = useState(false)

  const load = () =>
    api
      .get('/users')
      .then((res) => setUsers(res.data))
      .catch(() => setError('Impossible de charger les utilisateurs'))
      .finally(() => setLoading(false))

  useEffect(() => {
    load()
  }, [])

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  const handleInvite = async (e) => {
    e.preventDefault()
    setError('')
    setNotice('')
    setInviteUrl('')
    setSubmitting(true)
    try {
      const res = await api.post('/users/invite', form)
      setInviteUrl(buildInviteUrl(res.data.inviteUrl))
      setNotice(`Invitation envoyée à ${form.email}`)
      setForm({ firstName: '', lastName: '', email: '', role: 'GESTIONNAIRE' })
      await load()
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || 'L’invitation a échoué')
    } finally {
      setSubmitting(false)
    }
  }

  const update = async (id, body) => {
    setError('')
    setNotice('')
    setBusyId(id)
    try {
      await api.put(`/users/${id}`, body)
      setNotice('Utilisateur mis à jour')
      await load()
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || 'La mise à jour a échoué')
    } finally {
      setBusyId(null)
    }
  }

  const remove = async (u) => {
    if (!window.confirm(`Supprimer définitivement le compte de ${u.displayName} ?`)) return
    setError('')
    setNotice('')
    setBusyId(u.id)
    try {
      await api.delete(`/users/${u.id}`)
      setNotice('Compte supprimé')
      await load()
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || 'La suppression a échoué')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Utilisateurs</h2>
          <p>Gérez les accès à la plateforme pour votre société</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}
      {inviteUrl && (
        <div className="alert alert-info">
          Lien d’acceptation à transmettre au nouvel utilisateur :
          <div className="invite-url">{inviteUrl}</div>
        </div>
      )}

      <div className="card">
        <div className="card-title">
          <h3>Inviter un utilisateur</h3>
        </div>
        <form onSubmit={handleInvite}>
          <div className="data-grid">
            <div className="form-field">
              <label>Prénom *</label>
              <input
                type="text"
                value={form.firstName}
                required
                onChange={set('firstName')}
                placeholder="Marie"
              />
            </div>
            <div className="form-field">
              <label>Nom *</label>
              <input
                type="text"
                value={form.lastName}
                required
                onChange={set('lastName')}
                placeholder="Dubois"
              />
            </div>
            <div className="form-field">
              <label>Email *</label>
              <input
                type="email"
                value={form.email}
                required
                onChange={set('email')}
                placeholder="marie@transports.fr"
              />
            </div>
            <div className="form-field">
              <label>Rôle *</label>
              <select value={form.role} onChange={set('role')}>
                <option value="GESTIONNAIRE">Gestionnaire</option>
                <option value="ADMIN">Administrateur</option>
              </select>
            </div>
          </div>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Envoi…' : 'Envoyer l’invitation'}
            </button>
          </div>
        </form>
      </div>

      <div className="card desktop-table">
        <div className="card-title">
          <h3>Membres de la société</h3>
          <span className="muted">{users.length} utilisateur(s)</span>
        </div>
        {loading ? (
          <p className="muted table-empty">Chargement…</p>
        ) : (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Utilisateur</th>
                  <th>Email</th>
                  <th>Rôle</th>
                  <th>Statut</th>
                  <th>Créé le</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => {
                  const isSelf = me?.username === u.username
                  return (
                    <tr key={u.id}>
                      <td>
                        <strong>{u.displayName}</strong>
                        {isSelf && <span className="muted block">(vous)</span>}
                      </td>
                      <td>{u.email || u.username}</td>
                      <td>
                        <select
                          className="form-select"
                          value={u.role}
                          disabled={busyId === u.id || isSelf}
                          onChange={(e) => update(u.id, { role: e.target.value })}
                        >
                          <option value="GESTIONNAIRE">Gestionnaire</option>
                          <option value="ADMIN">Administrateur</option>
                        </select>
                      </td>
                      <td>
                        <span className={`badge ${u.enabled ? 'badge-green' : 'badge-red'}`}>
                          {u.enabled ? 'Actif' : 'Désactivé'}
                        </span>
                      </td>
                      <td>{fmtDate(u.createdAt)}</td>
                      <td>
                        <div className="row-actions">
                          <button
                            className="btn btn-outline btn-sm"
                            disabled={busyId === u.id || isSelf}
                            onClick={() => update(u.id, { enabled: !u.enabled })}
                          >
                            {u.enabled ? 'Désactiver' : 'Réactiver'}
                          </button>
                          <button
                            className="btn btn-danger btn-sm"
                            disabled={busyId === u.id || isSelf}
                            onClick={() => remove(u)}
                          >
                            Supprimer
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
