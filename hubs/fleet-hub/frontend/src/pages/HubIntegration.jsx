import { useEffect, useState } from 'react'
import api from '../services/api'

export default function HubIntegration() {
  const [enabled, setEnabled] = useState(false)
  const [newKey, setNewKey] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = () =>
    api
      .get('/internal-integrations/settings')
      .then((res) => setEnabled(res.data.internalIntegrationEnabled))
      .catch(() => setError("Impossible de charger le statut de l'intégration"))
      .finally(() => setLoading(false))

  useEffect(() => {
    load()
  }, [])

  const activate = async () => {
    setError('')
    setNotice('')
    setBusy(true)
    try {
      const res = await api.post('/internal-integrations/enable')
      setEnabled(true)
      setNewKey(res.data.internalIntegrationApiKey)
    } catch (err) {
      setError(err.response?.data?.message || "L'activation a échoué")
    } finally {
      setBusy(false)
    }
  }

  const deactivate = async () => {
    if (
      !window.confirm(
        'Désactiver cette intégration ? Compliance Engine et Logistix ne pourront plus lire vos expéditions.'
      )
    )
      return
    setError('')
    setNotice('')
    setBusy(true)
    try {
      await api.post('/internal-integrations/disable')
      setEnabled(false)
      setNewKey(null)
      setNotice('Intégration désactivée')
    } catch (err) {
      setError(err.response?.data?.message || 'La désactivation a échoué')
    } finally {
      setBusy(false)
    }
  }

  const copyKey = async () => {
    try {
      await navigator.clipboard.writeText(newKey)
      setNotice('Copié dans le presse-papiers')
    } catch {
      setError('Impossible de copier — copiez le texte manuellement')
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Logistix & Compliance Engine</h2>
          <p>Autorisez ces modules Praxio à lire vos expéditions pour leurs propres documents</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      <div className="card">
        <div className="card-title">
          <h3>Qu'est-ce qui est partagé ?</h3>
        </div>
        <p className="muted">
          Une fois activé, Compliance Engine (génération de factures douanières) et Logistix
          (réception d'entrepôt) peuvent lire — via une clé propre à votre société, jamais un accès
          direct à vos données — le détail et les articles d'une expédition précise qu'ils vous
          désignent par son identifiant. Aucune liste ni recherche n'est possible avec cette clé.
          Elle donne aussi accès à un résumé d'activité agrégé (expéditions actives, camions
          disponibles, réservations en cours) pour le tableau de bord cross-hub de Praxio.
        </p>
      </div>

      <div className="card">
        <div className="card-title">
          <h3>Statut de l'intégration</h3>
          {!loading && (
            <span className={`badge ${enabled ? 'badge-green' : 'badge-gray'}`}>
              {enabled ? 'Active' : 'Désactivée'}
            </span>
          )}
        </div>

        {loading ? (
          <p className="muted table-empty">Chargement…</p>
        ) : (
          <>
            <p className="muted">
              {enabled
                ? 'Compliance Engine et Logistix peuvent lire vos expéditions avec la clé ci-dessous.'
                : "Aucune lecture externe de vos expéditions n'est possible tant que l'intégration n'est pas activée."}
            </p>
            {enabled ? (
              <button className="btn btn-outline" disabled={busy} onClick={deactivate}>
                {busy ? 'Désactivation…' : "Désactiver l'intégration"}
              </button>
            ) : (
              <button className="btn btn-primary" disabled={busy} onClick={activate}>
                {busy ? 'Activation…' : "Activer l'intégration"}
              </button>
            )}
          </>
        )}
      </div>

      {newKey && (
        <div className="card">
          <div className="card-title">
            <h3>Votre clé d'intégration</h3>
            <span className="muted">Montrée une seule fois — notez-la maintenant</span>
          </div>
          <div className="form-field">
            <label>Clé d'intégration interne (X-Internal-Integration-Key)</label>
            <div className="copy-row">
              <code>{newKey}</code>
              <button className="btn btn-outline btn-sm" onClick={copyKey}>
                Copier
              </button>
            </div>
          </div>
          <p className="muted">
            Collez cette clé dans les paramètres de <strong>Compliance Engine</strong> ou de{' '}
            <strong>Logistix</strong> (section « Intégration Fleet Hub »). Si vous la perdez,
            désactivez puis réactivez l'intégration ci-dessus pour la retrouver — la clé ne change
            pas.
          </p>
        </div>
      )}
    </div>
  )
}
