import { useEffect, useMemo, useState } from 'react'
import api, { resolveApiBase } from '../services/api'

const CATEGORY_LABEL = {
  GPS: 'Suivi GPS',
  TACHOGRAPH: 'Tachygraphe',
  FUEL: 'Carburant',
  DHL: 'Transporteur'
}

const fmtDate = (s) => (s ? new Date(s).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—')

const emptyForm = () => ({
  provider: '',
  baseUrl: '',
  apiKey: '',
  enabled: true,
  settings: {}
})

const webhookUrl = () => `${resolveApiBase()}/webhooks/ingest`

export default function Integrations() {
  const [configs, setConfigs] = useState([])
  const [providers, setProviders] = useState([])
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(true)
  const [editingId, setEditingId] = useState(null)
  const [busyId, setBusyId] = useState(null)
  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState(null)
  const [form, setForm] = useState(emptyForm())
  const [submitting, setSubmitting] = useState(false)

  const load = () =>
    Promise.all([api.get('/integrations'), api.get('/integrations/providers')])
      .then(([c, p]) => {
        setConfigs(c.data)
        setProviders(p.data)
      })
      .catch(() => setError('Impossible de charger les intégrations'))
      .finally(() => setLoading(false))

  useEffect(() => {
    load()
  }, [])

  const providerMeta = (name) => providers.find((p) => p.name === name)

  const groupedProviders = useMemo(() => {
    const groups = {}
    for (const p of providers) {
      if (!groups[p.category]) groups[p.category] = []
      groups[p.category].push(p)
    }
    return groups
  }, [providers])

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  const chooseProvider = (name) => {
    const meta = providers.find((p) => p.name === name)
    setForm({
      ...form,
      provider: name,
      baseUrl: meta?.defaultBaseUrl || '',
      settings: {}
    })
    setTestResult(null)
  }

  const setSetting = (key) => (e) =>
    setForm({ ...form, settings: { ...form.settings, [key]: e.target.value } })

  const resetForm = () => {
    setForm(emptyForm())
    setEditingId(null)
    setTestResult(null)
    setError('')
  }

  const edit = (cfg) => {
    setError('')
    setNotice('')
    setEditingId(cfg.id)
    setForm({
      provider: cfg.provider,
      baseUrl: cfg.baseUrl || '',
      apiKey: '',
      enabled: cfg.enabled,
      settings: cfg.settings || {}
    })
    setTestResult(null)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const testDraft = async () => {
    setError('')
    setTestResult(null)
    if (!form.provider || !form.baseUrl) {
      setError('Renseignez au moins le fournisseur et l’URL avant de tester')
      return
    }
    setTesting(true)
    try {
      const res = await api.post('/integrations/test', {
        provider: form.provider,
        baseUrl: form.baseUrl,
        apiKey: form.apiKey,
        enabled: form.enabled,
        settings: form.settings
      })
      setTestResult(res.data)
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || 'Le test de connexion a échoué')
    } finally {
      setTesting(false)
    }
  }

  const testSaved = async (cfg) => {
    setError('')
    setNotice('')
    setBusyId(cfg.id)
    try {
      const res = await api.post(`/integrations/${cfg.id}/test`)
      setNotice(
        res.data.ok
          ? `Connexion réussie en ${res.data.latencyMs} ms`
          : `Échec de connexion : ${res.data.message}`
      )
      await load()
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || 'Le test de connexion a échoué')
    } finally {
      setBusyId(null)
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setNotice('')
    setTestResult(null)
    setSubmitting(true)
    const body = {
      provider: form.provider,
      baseUrl: form.baseUrl,
      apiKey: form.apiKey || null,
      enabled: form.enabled,
      settings: Object.keys(form.settings).length ? form.settings : null
    }
    try {
      if (editingId) {
        await api.put(`/integrations/${editingId}`, body)
        setNotice('Intégration mise à jour')
      } else {
        await api.post('/integrations', body)
        setNotice('Intégration créée')
      }
      resetForm()
      await load()
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || 'L’enregistrement a échoué')
    } finally {
      setSubmitting(false)
    }
  }

  const toggleEnabled = async (cfg) => {
    setBusyId(cfg.id)
    try {
      await api.put(`/integrations/${cfg.id}`, {
        provider: cfg.provider,
        baseUrl: cfg.baseUrl,
        enabled: !cfg.enabled,
        settings: cfg.settings || null
      })
      await load()
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || 'La mise à jour a échoué')
    } finally {
      setBusyId(null)
    }
  }

  const remove = async (cfg) => {
    if (!window.confirm(`Supprimer l’intégration « ${cfg.providerLabel} » ?`)) return
    setError('')
    setNotice('')
    setBusyId(cfg.id)
    try {
      await api.delete(`/integrations/${cfg.id}`)
      setNotice('Intégration supprimée')
      if (editingId === cfg.id) resetForm()
      await load()
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || 'La suppression a échoué')
    } finally {
      setBusyId(null)
    }
  }

  const copyText = async (text) => {
    try {
      await navigator.clipboard.writeText(text)
      setNotice('Copié dans le presse-papiers')
    } catch {
      setError('Impossible de copier — copiez le texte manuellement')
    }
  }

  const meta = providerMeta(form.provider)

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Intégrations</h2>
          <p>Connectez vos fournisseurs de données (GPS, tachygraphe, carburant, transporteur)</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      <div className="card">
        <div className="card-title">
          <h3>{editingId ? 'Modifier l’intégration' : 'Ajouter une intégration'}</h3>
          <span className="muted">
            Les clés API sont chiffrées et jamais réaffichées
          </span>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="data-grid">
            <div className="form-field">
              <label>Fournisseur *</label>
              <select value={form.provider} required onChange={(e) => chooseProvider(e.target.value)}>
                <option value="" disabled>
                  Choisir un fournisseur…
                </option>
                {Object.entries(groupedProviders).map(([category, list]) => (
                  <optgroup key={category} label={CATEGORY_LABEL[category] || category}>
                    {list.map((p) => (
                      <option key={p.name} value={p.name}>
                        {p.label}
                      </option>
                    ))}
                  </optgroup>
                ))}
              </select>
            </div>
            <div className="form-field">
              <label>URL de base *</label>
              <input
                type="url"
                value={form.baseUrl}
                required
                onChange={set('baseUrl')}
                placeholder="https://api.fournisseur.com"
              />
            </div>
            <div className="form-field">
              <label>Clé API {editingId ? '(laisser vide pour conserver)' : ''}</label>
              <input
                type="password"
                value={form.apiKey}
                autoComplete="new-password"
                onChange={set('apiKey')}
                placeholder={editingId ? '••••••••' : 'Clé fournie par le fournisseur'}
              />
            </div>
            {meta?.fields.map((field) => (
              <div className="form-field" key={field.name}>
                <label>{field.label}</label>
                <input
                  type="text"
                  value={form.settings[field.name] || ''}
                  onChange={setSetting(field.name)}
                  placeholder={field.placeholder}
                />
              </div>
            ))}
          </div>
          <label className="toggle-field">
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
            />
            <span>Activer l’intégration</span>
          </label>
          <div className="form-actions">
            <button type="button" className="btn btn-outline" disabled={testing} onClick={testDraft}>
              {testing ? 'Test en cours…' : 'Tester la connexion'}
            </button>
            {editingId && (
              <button type="button" className="btn btn-outline" onClick={resetForm}>
                Annuler
              </button>
            )}
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Enregistrement…' : editingId ? 'Enregistrer' : 'Créer l’intégration'}
            </button>
          </div>
          {testResult && (
            <div className={`alert ${testResult.ok ? '' : 'alert-error'}`}>
              <strong>{testResult.ok ? 'Connexion réussie' : 'Connexion impossible'}</strong>
              <span className="muted block">
                {testResult.message} — {testResult.latencyMs} ms
                {testResult.statusCode ? ` (HTTP ${testResult.statusCode})` : ''}
              </span>
            </div>
          )}
        </form>
      </div>

      <div className="card desktop-table">
        <div className="card-title">
          <h3>Intégrations configurées</h3>
          <span className="muted">{configs.length} configuration(s)</span>
        </div>
        {loading ? (
          <p className="muted table-empty">Chargement…</p>
        ) : configs.length === 0 ? (
          <p className="muted table-empty">Aucune intégration — créez-en une ci-dessus</p>
        ) : (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Fournisseur</th>
                  <th>URL</th>
                  <th>Clé API</th>
                  <th>Dernier test</th>
                  <th>Statut</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {configs.map((cfg) => (
                  <tr key={cfg.id}>
                    <td>
                      <strong>{cfg.providerLabel}</strong>
                      <span className="muted block">{CATEGORY_LABEL[cfg.category] || cfg.category}</span>
                    </td>
                    <td className="muted">{cfg.baseUrl}</td>
                    <td>{cfg.hasApiKey ? cfg.apiKeyMasked : <span className="muted">—</span>}</td>
                    <td>
                      {cfg.lastTestAt ? (
                        <>
                          <span className={`badge ${cfg.lastTestOk ? 'badge-green' : 'badge-red'}`}>
                            {cfg.lastTestOk ? 'OK' : 'Échec'}
                          </span>
                          <span className="muted block">{fmtDate(cfg.lastTestAt)}</span>
                        </>
                      ) : (
                        <span className="muted">Jamais testé</span>
                      )}
                    </td>
                    <td>
                      <span className={`badge ${cfg.enabled ? 'badge-green' : 'badge-gray'}`}>
                        {cfg.enabled ? 'Active' : 'Désactivée'}
                      </span>
                    </td>
                    <td>
                      <div className="row-actions">
                        <button
                          className="btn btn-outline btn-sm"
                          disabled={busyId === cfg.id}
                          onClick={() => testSaved(cfg)}
                        >
                          Tester
                        </button>
                        <button className="btn btn-outline btn-sm" onClick={() => edit(cfg)}>
                          Modifier
                        </button>
                        <button
                          className="btn btn-outline btn-sm"
                          disabled={busyId === cfg.id}
                          onClick={() => toggleEnabled(cfg)}
                        >
                          {cfg.enabled ? 'Désactiver' : 'Activer'}
                        </button>
                        <button
                          className="btn btn-danger btn-sm"
                          disabled={busyId === cfg.id}
                          onClick={() => remove(cfg)}
                        >
                          Supprimer
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {configs.filter((c) => c.enabled && c.webhookKey).length > 0 && (
        <div className="card">
          <div className="card-title">
            <h3>Webhook de poussée (push)</h3>
            <span className="muted">
              Transmettez ces informations à votre fournisseur pour recevoir les données
            </span>
          </div>
          <div className="webhook-info">
            <div className="form-field">
              <label>URL d’envoi (POST)</label>
              <div className="copy-row">
                <code>{webhookUrl()}</code>
                <button className="btn btn-outline btn-sm" onClick={() => copyText(webhookUrl())}>
                  Copier
                </button>
              </div>
            </div>
            {configs
              .filter((c) => c.enabled && c.webhookKey)
              .map((cfg) => (
                <div className="form-field" key={cfg.id}>
                  <label>Clé webhook — {cfg.providerLabel}</label>
                  <div className="copy-row">
                    <code>{cfg.webhookKey}</code>
                    <button className="btn btn-outline btn-sm" onClick={() => copyText(cfg.webhookKey)}>
                      Copier
                    </button>
                  </div>
                </div>
              ))}
            <p className="muted">
              En-tête <code>X-API-Key</code> requis. Les données reçues sont rattachées
              automatiquement à votre société (camions et chauffeurs correspondants).
            </p>
          </div>
        </div>
      )}
    </div>
  )
}
