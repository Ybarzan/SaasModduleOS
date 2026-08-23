import { useEffect, useState } from 'react'
import api from '../services/api'
import { useAuth } from '../context/AuthContext'

const fmtDate = (s) => (s ? new Date(s).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—')

export default function Privacy() {
  const { user, logout } = useAuth()
  const [logs, setLogs] = useState([])
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(true)
  const [exporting, setExporting] = useState(false)
  const [password, setPassword] = useState('')
  const [deleting, setDeleting] = useState(false)
  const [confirmText, setConfirmText] = useState('')

  const load = () =>
    api
      .get('/account/audit-log')
      .then((res) => setLogs(res.data))
      .catch(() => setError('Impossible de charger le journal d’audit'))
      .finally(() => setLoading(false))

  useEffect(() => {
    load()
  }, [])

  const exportData = async () => {
    setError('')
    setNotice('')
    setExporting(true)
    try {
      const res = await api.get('/account/export')
      const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `export-rgpd-fleet-hub-${new Date().toISOString().slice(0, 10)}.json`
      a.click()
      URL.revokeObjectURL(url)
      setNotice('Export généré : le fichier JSON a été téléchargé')
    } catch {
      setError('L’export des données a échoué')
    } finally {
      setExporting(false)
    }
  }

  const handleDelete = async (e) => {
    e.preventDefault()
    setError('')
    setNotice('')
    setDeleting(true)
    try {
      await api.post('/account/delete', { password })
      logout()
      window.location.href = '/login'
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || 'La suppression a échoué')
    } finally {
      setDeleting(false)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Mes données (RGPD)</h2>
          <p>Portabilité, journal d’audit et suppression de votre compte</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      <div className="card">
        <div className="card-title">
          <h3>Portabilité (art. 20)</h3>
          <span className="muted">Téléchargez l’ensemble de vos données au format JSON</span>
        </div>
        <div className="form-actions">
          <button className="btn btn-primary" disabled={exporting} onClick={exportData}>
            {exporting ? 'Génération…' : '⬇ Exporter toutes mes données'}
          </button>
        </div>
      </div>

      <div className="card desktop-table">
        <div className="card-title">
          <h3>Journal d’audit</h3>
          <span className="muted">{logs.length} événement(s)</span>
        </div>
        {loading ? (
          <p className="muted table-empty">Chargement…</p>
        ) : (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Utilisateur</th>
                  <th>Action</th>
                  <th>Détail</th>
                  <th>Adresse IP</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((l) => (
                  <tr key={l.id}>
                    <td>{fmtDate(l.createdAt)}</td>
                    <td>{l.username}</td>
                    <td>
                      <span className="badge badge-blue">{l.action}</span>
                    </td>
                    <td className="muted">{l.detail}</td>
                    <td>{l.ipAddress || '—'}</td>
                  </tr>
                ))}
                {logs.length === 0 && (
                  <tr>
                    <td colSpan="5" className="muted table-empty">Aucun événement</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="card">
        <div className="card-title">
          <h3>Suppression du compte (art. 17)</h3>
          <span className="muted">Effacement définitif de la société et de toutes ses données</span>
        </div>
        <div className="alert alert-warning">
          Cette action est <strong>irréversible</strong> : toutes les données (chauffeurs, camions, trajets,
          carburant, notifications) seront supprimées et l’abonnement Stripe résilié.
        </div>
        <form onSubmit={handleDelete}>
          <input
            type="text"
            className="sr-only"
            value={user?.username || ''}
            autoComplete="username"
            readOnly
            tabIndex={-1}
            aria-hidden="true"
          />
          <label>
            Confirmez en saisissant « SUPPRIMER »
            <input
              type="text"
              value={confirmText}
              onChange={(e) => setConfirmText(e.target.value)}
              placeholder="SUPPRIMER"
              autoComplete="off"
            />
          </label>
          <label>
            Mot de passe (confirmation)
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
            />
          </label>
          <div className="form-actions">
            <button
              type="submit"
              className="btn btn-danger"
              disabled={deleting || confirmText !== 'SUPPRIMER' || !password}
            >
              {deleting ? 'Suppression…' : 'Supprimer définitivement mon compte'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
