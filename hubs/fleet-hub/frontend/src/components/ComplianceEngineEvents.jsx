import { useEffect, useState } from 'react'
import api from '../services/api'

/**
 * Envoi des événements d'expédition (création, changement de statut) au moteur de
 * règles de Compliance Engine : clé webhook à coller, état de la file d'envoi
 * (voir OutboundEventDispatcher côté backend) et relance des événements abandonnés.
 */
export default function ComplianceEngineEvents() {
  const [settings, setSettings] = useState(null)
  const [key, setKey] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    api
      .get('/compliance-engine-webhook/settings')
      .then((res) => setSettings(res.data))
      .catch(() => setError("Impossible de charger l'état de l'envoi d'événements"))
  }, [])

  const run = async (action, success, failure) => {
    setError('')
    setNotice('')
    setBusy(true)
    try {
      const res = await action()
      setSettings(res.data)
      if (success) setNotice(success)
      return true
    } catch (err) {
      setError(err.response?.data?.message || failure)
      return false
    } finally {
      setBusy(false)
    }
  }

  const save = (e) => {
    e.preventDefault()
    run(
      () => api.put('/compliance-engine-webhook/settings', { webhookKey: key.trim() }),
      'Clé enregistrée : les prochains événements seront envoyés',
      "L'enregistrement de la clé a échoué"
    ).then((ok) => ok && setKey(''))
  }

  const clear = () => {
    if (!window.confirm("Retirer la clé ? Les événements d'expédition ne seront plus envoyés."))
      return
    run(
      () => api.delete('/compliance-engine-webhook/settings'),
      'Clé retirée',
      'Le retrait a échoué'
    )
  }

  const retry = () =>
    run(
      () => api.post('/compliance-engine-webhook/retry-failed'),
      'Événements remis en file d’envoi',
      'La relance a échoué'
    )

  return (
    <div className="card">
      <div className="card-title">
        <h3>Événements vers Compliance Engine</h3>
        {settings && (
          <span className={`badge ${settings.configured ? 'badge-green' : 'badge-gray'}`}>
            {settings.configured ? 'Actif' : 'Non configuré'}
          </span>
        )}
      </div>
      <p className="muted">
        Les créations et changements de statut d’expédition alimentent le moteur de règles de
        Compliance Engine. Ils sont envoyés en tâche de fond, avec de nouvelles tentatives si
        Compliance Engine ne répond pas, et jamais deux fois.
      </p>
      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      {settings && (
        <>
          <form onSubmit={save}>
            <div className="form-field">
              <label htmlFor="compliance-engine-webhook-key">
                Clé webhook Compliance Engine {settings.configured ? '(remplacer)' : ''}
              </label>
              <input
                id="compliance-engine-webhook-key"
                type="password"
                autoComplete="off"
                value={key}
                onChange={(e) => setKey(e.target.value)}
                placeholder="générée côté Compliance Engine (POST /v1/fleethub-webhook/enable)"
              />
            </div>
            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={busy || !key.trim()}>
                Enregistrer la clé
              </button>
              {settings.configured && (
                <button type="button" className="btn btn-outline" disabled={busy} onClick={clear}>
                  Retirer la clé
                </button>
              )}
            </div>
          </form>

          <div className="data-grid" style={{ marginTop: 16 }}>
            <div>
              <span className="muted block">En attente d’envoi</span>
              <strong>{settings.pendingEvents}</strong>
            </div>
            <div>
              <span className="muted block">Abandonnés</span>
              <strong>{settings.failedEvents}</strong>
            </div>
          </div>
          {settings.lastError && (
            <p className="muted">
              Dernière erreur : <code>{settings.lastError}</code>
            </p>
          )}
          {settings.failedEvents > 0 && (
            <button className="btn btn-outline btn-sm" disabled={busy} onClick={retry}>
              Relancer les événements abandonnés
            </button>
          )}
        </>
      )}
    </div>
  )
}
