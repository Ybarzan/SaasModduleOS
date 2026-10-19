import { useEffect, useState } from 'react'
import api from '../services/api'
import StatCard from '../components/StatCard'

const PROVIDER_DEFS = [
  {
    type: 'SHIPPO',
    name: 'Shippo',
    description: 'API multi-transporteurs : UPS, FedEx, USPS, DHL + 100 transporteurs',
    icon: '📦',
    features: ['100+ transporteurs', 'Tarifs temps réel', 'Tracking intégré'],
    needsSecret: false
  },
  {
    type: 'DHL',
    name: 'DHL Express',
    description: 'API officielle DHL — Express & Freight',
    icon: '🚚',
    features: ['Réseau mondial', 'Express 1-3 jours', 'Marchandises dangereuses'],
    needsSecret: true
  },
  {
    type: 'INTERNAL',
    name: 'Tarifs internes',
    description: 'Tarifs configurés manuellement dans vos transporteurs',
    icon: '🏢',
    features: ['Sans clé API', 'Configuration manuelle', 'Toujours actif'],
    needsSecret: false
  }
]

const HEALTH_LABELS = {
  HEALTHY: 'Sain',
  DEGRADED: 'Dégradé',
  DOWN: 'Hors ligne',
  UNKNOWN: 'Inconnu'
}
const HEALTH_COLORS = { HEALTHY: 'green', DEGRADED: 'orange', DOWN: 'red', UNKNOWN: 'gray' }

export default function Providers() {
  const [providers, setProviders] = useState([])
  const [health, setHealth] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [connecting, setConnecting] = useState(null)
  const [apiKey, setApiKey] = useState('')
  const [apiSecret, setApiSecret] = useState('')
  const [priority, setPriority] = useState(5)
  const [busyId, setBusyId] = useState(null)

  const load = () => {
    setLoading(true)
    api
      .get('/providers')
      .then((res) => setProviders(res.data || []))
      .catch(() => setError('Impossible de charger les fournisseurs'))
      .finally(() => setLoading(false))
    api
      .get('/providers/health')
      .then((res) => setHealth(res.data || []))
      .catch(() => {})
  }

  useEffect(() => {
    load()
    const id = setInterval(() => {
      api
        .get('/providers/health')
        .then((res) => setHealth(res.data || []))
        .catch(() => {})
    }, 30000)
    return () => clearInterval(id)
  }, [])

  const getConfig = (type) => providers.find((p) => p.providerType === type)
  const getHealth = (type) => health.find((h) => h.providerType === type)

  const openConnect = (type, config) => {
    setApiKey(config?.apiKey || '')
    setApiSecret('')
    setPriority(config?.priority || 5)
    setConnecting(type)
  }

  const closeConnect = () => {
    setConnecting(null)
    setApiKey('')
    setApiSecret('')
    setPriority(5)
  }

  const handleConnect = async (e) => {
    e.preventDefault()
    setError('')
    setNotice('')
    if (!apiKey.trim()) {
      setError('La clé API est requise')
      return
    }
    setBusyId('connect')
    try {
      await api.post('/providers', {
        providerType: connecting,
        apiKey: apiKey.trim(),
        apiSecret: apiSecret.trim() || undefined,
        priority,
        isActive: true
      })
      setNotice('Fournisseur connecté avec succès')
      closeConnect()
      load()
    } catch {
      setError('Erreur lors de la connexion du fournisseur')
    } finally {
      setBusyId(null)
    }
  }

  const test = async (id) => {
    setBusyId(id)
    try {
      await api.post(`/providers/${id}/test`)
      setNotice('Connexion testée avec succès')
      load()
    } catch {
      setError('Échec du test de connexion')
    } finally {
      setBusyId(null)
    }
  }

  const disconnect = async (id) => {
    if (
      !window.confirm(
        'Déconnecter ce fournisseur ? Les devis de cette source ne seront plus disponibles.'
      )
    )
      return
    setBusyId(id)
    try {
      await api.delete(`/providers/${id}`)
      setNotice('Fournisseur déconnecté')
      load()
    } catch {
      setError('Erreur lors de la déconnexion')
    } finally {
      setBusyId(null)
    }
  }

  const runHealthCheck = async () => {
    setBusyId('health')
    try {
      const res = await api.get('/providers/health')
      setHealth(res.data || [])
      setNotice('Vérification de santé effectuée')
    } catch {
      setError('Erreur lors de la vérification')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Fournisseurs de tarifs</h2>
          <p>Connectez vos API de transport pour des devis en temps réel</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      {loading && (
        <div className="loading-spinner" aria-label="Chargement des fournisseurs…">
          Chargement…
        </div>
      )}

      <div className="couple-cards">
        {PROVIDER_DEFS.map((def) => {
          const config = getConfig(def.type)
          const h = getHealth(def.type)
          const isInternal = def.type === 'INTERNAL'
          const isConnected = !!config && config.active
          const statusKey = h ? h.healthStatus : isInternal ? 'HEALTHY' : 'UNKNOWN'

          return (
            <div key={def.type} className="couple-card">
              <div className="couple-card-top">
                <div>
                  <strong>
                    {def.icon} {def.name}
                  </strong>
                  <span className="muted block">{def.description}</span>
                </div>
              </div>
              <div className="chip-row" style={{ marginBottom: 10 }}>
                <span className={`badge badge-${HEALTH_COLORS[statusKey]}`}>
                  {HEALTH_LABELS[statusKey]}
                </span>
                {isInternal && <span className="badge badge-green">Toujours actif</span>}
                {isConnected && !isInternal && config?.consecutiveFailures > 0 && (
                  <span className="badge badge-red">{config.consecutiveFailures} échec(s)</span>
                )}
              </div>
              <ul className="muted" style={{ fontSize: 13, marginBottom: 14, paddingLeft: 18 }}>
                {def.features.map((f) => (
                  <li key={f}>{f}</li>
                ))}
              </ul>
              {!isInternal && (
                <div className="row-actions" style={{ justifyContent: 'flex-start' }}>
                  {isConnected ? (
                    <>
                      <button
                        className="btn btn-outline btn-sm"
                        onClick={() => openConnect(def.type, config)}
                      >
                        Reconnecter
                      </button>
                      <button
                        className="btn btn-outline btn-sm"
                        disabled={busyId === config.id}
                        onClick={() => test(config.id)}
                      >
                        Tester
                      </button>
                      <button
                        className="btn btn-danger btn-sm"
                        disabled={busyId === config.id}
                        onClick={() => disconnect(config.id)}
                      >
                        Déconnecter
                      </button>
                    </>
                  ) : (
                    <button
                      className="btn btn-primary btn-sm"
                      onClick={() => openConnect(def.type, null)}
                    >
                      Connecter
                    </button>
                  )}
                </div>
              )}
            </div>
          )
        })}
      </div>

      {connecting && (
        <div className="card">
          <div className="card-title">
            <h3>Connecter {PROVIDER_DEFS.find((d) => d.type === connecting)?.name}</h3>
          </div>
          <form onSubmit={handleConnect}>
            <div className="data-grid">
              <div className="form-field">
                <label>Clé API *</label>
                <input
                  type="password"
                  value={apiKey}
                  required
                  onChange={(e) => setApiKey(e.target.value)}
                  placeholder="Entrez votre clé API"
                />
              </div>
              {PROVIDER_DEFS.find((d) => d.type === connecting)?.needsSecret && (
                <div className="form-field">
                  <label>Clé secrète</label>
                  <input
                    type="password"
                    value={apiSecret}
                    onChange={(e) => setApiSecret(e.target.value)}
                    placeholder="Clé secrète"
                  />
                </div>
              )}
              <div className="form-field">
                <label>Priorité : {priority}</label>
                <input
                  type="range"
                  min="1"
                  max="10"
                  value={priority}
                  onChange={(e) => setPriority(parseInt(e.target.value))}
                />
              </div>
            </div>
            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={busyId === 'connect'}>
                Sauvegarder
              </button>
              <button type="button" className="btn btn-outline" onClick={closeConnect}>
                Annuler
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="card desktop-table">
        <div className="card-title">
          <h3>État de santé</h3>
          <button
            className="btn btn-outline btn-sm"
            disabled={busyId === 'health'}
            onClick={runHealthCheck}
          >
            🔄 Vérifier la santé
          </button>
        </div>
        <div className="table-scroll">
          <table className="table table-hover">
            <thead>
              <tr>
                <th>Fournisseur</th>
                <th>Statut</th>
                <th>Dernière vérification</th>
                <th>Échecs consécutifs</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {health.length > 0 ? (
                health.map((h) => {
                  const config = getConfig(h.providerType)
                  return (
                    <tr key={h.providerType}>
                      <td className="cell-strong">
                        <strong>{h.providerName}</strong>
                        <span className="muted block">{h.providerType}</span>
                      </td>
                      <td>
                        <span className={`badge badge-${HEALTH_COLORS[h.healthStatus]}`}>
                          {HEALTH_LABELS[h.healthStatus]}
                        </span>
                      </td>
                      <td className="muted">
                        {h.lastHealthCheck
                          ? new Date(h.lastHealthCheck).toLocaleString('fr-FR')
                          : '—'}
                      </td>
                      <td className={h.consecutiveFailures > 0 ? 'text-danger' : 'muted'}>
                        {h.consecutiveFailures}
                      </td>
                      <td>
                        {config && (
                          <div className="row-actions">
                            <button
                              className="btn btn-outline btn-sm"
                              disabled={busyId === config.id}
                              onClick={() => test(config.id)}
                            >
                              Tester
                            </button>
                            {!h.active && (
                              <button
                                className="btn btn-danger btn-sm"
                                onClick={() => disconnect(config.id)}
                              >
                                Supprimer
                              </button>
                            )}
                          </div>
                        )}
                      </td>
                    </tr>
                  )
                })
              ) : (
                <tr>
                  <td colSpan={5} className="table-empty">
                    Aucun fournisseur configuré
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
