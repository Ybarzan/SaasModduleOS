import { useEffect, useState } from 'react'
import api from '../services/api'

const PLANS = ['TRIAL', 'STARTER', 'PRO', 'ENTERPRISE']

const planLimits = {
  TRIAL: '10 véh / 5 chauf',
  STARTER: '25 véh / 10 chauf',
  PRO: '100 véh / 50 chauf',
  ENTERPRISE: 'Illimité'
}

const statusBadge = {
  ACTIVE: 'badge-green',
  TRIAL: 'badge-blue',
  SUSPENDED: 'badge-red',
  CANCELLED: 'badge-gray'
}

const statusLabel = {
  ACTIVE: 'Active',
  TRIAL: 'Essai',
  SUSPENDED: 'Suspendue',
  CANCELLED: 'Annulée'
}

const fmtDate = (s) => (s ? new Date(s).toLocaleDateString('fr-FR') : '—')

export default function Admin() {
  const [companies, setCompanies] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState(null)
  const [ipList, setIpList] = useState([])
  const [newIp, setNewIp] = useState('')
  const [newIpLabel, setNewIpLabel] = useState('')
  const [ipError, setIpError] = useState('')

  const load = () =>
    api
      .get('/admin/companies')
      .then((res) => setCompanies(res.data))
      .catch(() => setError('Impossible de charger les sociétés'))
      .finally(() => setLoading(false))

  const loadIps = () =>
    api
      .get('/admin/ip-allowlist')
      .then((res) => setIpList(res.data))
      .catch(() => {})

  useEffect(() => {
    load()
    loadIps()
  }, [])

  const run = async (id, url, body) => {
    setError('')
    setBusyId(id)
    try {
      await api.post(`/admin/companies/${id}${url}`, body)
      await load()
    } catch {
      setError("L'action a \u00e9chou\u00e9")
    } finally {
      setBusyId(null)
    }
  }

  const suspend = (c) => run(c.id, '/suspend')
  const activate = (c) => run(c.id, '/activate')
  const setPlan = (c, plan) => run(c.id, '/plan', { plan })
  const extendTrial = (c) => run(c.id, '/extend-trial', { days: 30 })

  const addIp = async () => {
    setIpError('')
    const ip = newIp.trim()
    if (!ip) return
    try {
      await api.post('/admin/ip-allowlist', { ip, label: newIpLabel.trim() || null })
      setNewIp('')
      setNewIpLabel('')
      loadIps()
    } catch (err) {
      setIpError(err.response?.data?.message || 'Erreur lors de l\'ajout')
    }
  }

  const removeIp = async (id) => {
    try {
      await api.delete(`/admin/ip-allowlist/${id}`)
      loadIps()
    } catch {
      setIpError('Erreur lors de la suppression')
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Back-office plateforme</h2>
          <p>Sociétés clientes, abonnements et essais</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {loading && <p className="muted">Chargement…</p>}

      <div className="card desktop-table">
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th>Société</th>
                <th>Plan</th>
                <th>Statut</th>
                <th>Essai jusqu’au</th>
                <th>Utilisateurs</th>
                <th>Chauffeurs</th>
                <th>Camions</th>
                <th>Accès</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {companies.map((c) => (
                <tr key={c.id}>
                  <td>
                    <strong>{c.name}</strong>
                    {c.city && <span className="muted block">{c.city}{c.country ? `, ${c.country}` : ''}</span>}
                  </td>
                  <td>
                    <select
                      className="form-select"
                      value={c.plan}
                      disabled={busyId === c.id}
                      onChange={(e) => setPlan(c, e.target.value)}
                    >
                      {PLANS.map((p) => (
                        <option key={p} value={p}>
                          {p} ({planLimits[p]})
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <span className={`badge ${statusBadge[c.status] || 'badge-gray'}`}>
                      {statusLabel[c.status] || c.status}
                    </span>
                  </td>
                  <td>{fmtDate(c.trialEndsAt)}</td>
                  <td>{c.userCount}</td>
                  <td>{c.driverCount}</td>
                  <td>{c.truckCount}</td>
                  <td>
                    <span className={`badge ${c.loginAllowed ? 'badge-green' : 'badge-red'}`}>
                      {c.loginAllowed ? 'Autorisé' : 'Bloqué'}
                    </span>
                  </td>
                  <td>
                    <div className="admin-actions">
                      {c.status === 'SUSPENDED' ? (
                        <button className="btn btn-sm btn-primary" disabled={busyId === c.id} onClick={() => activate(c)}>
                          Réactiver
                        </button>
                      ) : (
                        <button className="btn btn-sm btn-danger" disabled={busyId === c.id} onClick={() => suspend(c)}>
                          Suspendre
                        </button>
                      )}
                      <button className="btn btn-sm btn-outline" disabled={busyId === c.id} onClick={() => extendTrial(c)}>
                        +30 j essai
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {companies.length === 0 && !loading && (
          <p className="muted table-empty">Aucune société inscrite</p>
        )}
      </div>

      <div className="card" style={{ marginTop: '1.5rem' }}>
        <div className="card-title">
          <h3>IP Allowlisting</h3>
          <span className="muted">Adresses IP autorisées pour /api/admin/**</span>
        </div>
        <p className="muted" style={{ marginBottom: '1rem', fontSize: '0.85rem' }}>
          Seules les IPs listées ici (ou dans la variable d'environnement <code>APP_ADMIN_ALLOWED_IPS</code>) peuvent accéder aux endpoints admin plateforme.
        </p>
        {ipError && <div className="alert alert-error">{ipError}</div>}

        <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
          <input
            className="form-input"
            placeholder="1.2.3.4"
            value={newIp}
            onChange={(e) => setNewIp(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && addIp()}
            style={{ maxWidth: '180px' }}
          />
          <input
            className="form-input"
            placeholder="Label (optionnel)"
            value={newIpLabel}
            onChange={(e) => setNewIpLabel(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && addIp()}
            style={{ maxWidth: '200px' }}
          />
          <button className="btn btn-sm btn-primary" onClick={addIp}>Ajouter</button>
        </div>

        {ipList.length > 0 ? (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Adresse IP</th>
                  <th>Label</th>
                  <th>Ajoutée le</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {ipList.map((entry) => (
                  <tr key={entry.id}>
                    <td><code>{entry.ipAddress}</code></td>
                    <td className="muted">{entry.label || '—'}</td>
                    <td className="muted">{fmtDate(entry.createdAt)}</td>
                    <td>
                      <button className="btn btn-sm btn-danger" onClick={() => removeIp(entry.id)}>
                        Supprimer
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="muted" style={{ fontSize: '0.85rem' }}>
            Aucune IP en base. L'accès admin est contrôlé uniquement par la variable d'environnement.
          </p>
        )}
      </div>
    </div>
  )
}
