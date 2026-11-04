import { useEffect, useState } from 'react'
import api from '../services/api'

/** Ce que chaque droit permet, en clair pour l'admin. */
const SCOPE_LABELS = {
  SHIPMENTS_READ: 'Lire les expéditions',
  SHIPMENTS_CREATE: 'Créer des brouillons',
  DASHBOARD_READ: "Résumé d'activité",
  BILLING_READ: 'Résumé de facturation'
}

/** Où coller la clé, côté hub consommateur. */
const WHERE_TO_PASTE = {
  COMPLIANCE_ENGINE: 'Compliance Engine › Paramètres société › Intégration Fleet Hub',
  LOGISTIX: 'Logistix › Paramètres › Intégration Fleet Hub',
  FINANCE: 'les paramètres de la société dans Finance (clé Fleet Hub)',
  PORTAL: 'Praxio › Tableau de bord › clés des hubs (champ Fleet Hub)'
}

const fmtDate = (iso) =>
  iso
    ? new Date(iso).toLocaleString('fr-FR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      })
    : '—'

export default function HubIntegration() {
  const [enabled, setEnabled] = useState(false)
  const [keys, setKeys] = useState([])
  const [newKey, setNewKey] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const apply = (data) => {
    setEnabled(data.internalIntegrationEnabled)
    setKeys(data.keys || [])
  }

  const load = () =>
    api
      .get('/internal-integrations/settings')
      .then((res) => apply(res.data))
      .catch(() => setError("Impossible de charger le statut de l'intégration"))
      .finally(() => setLoading(false))

  useEffect(() => {
    load()
  }, [])

  const run = async (action, failure) => {
    setError('')
    setNotice('')
    setBusy(true)
    try {
      return await action()
    } catch (err) {
      setError(err.response?.data?.message || failure)
    } finally {
      setBusy(false)
    }
  }

  const activate = () =>
    run(async () => {
      const res = await api.post('/internal-integrations/enable')
      apply(res.data)
    }, "L'activation a échoué")

  const deactivate = () => {
    if (
      !window.confirm(
        'Désactiver le partage ? Plus aucun hub ne pourra lire vos données tant que vous ne le réactivez pas (les clés sont conservées).'
      )
    )
      return
    run(async () => {
      const res = await api.post('/internal-integrations/disable')
      apply(res.data)
      setNewKey(null)
      setNotice('Partage désactivé')
    }, 'La désactivation a échoué')
  }

  const generate = (key) => {
    if (
      key.configured &&
      !window.confirm(
        `Remplacer la clé « ${key.label} » ? L'ancienne cesse de fonctionner immédiatement : collez la nouvelle dans ${key.label} juste après.`
      )
    )
      return
    run(async () => {
      const res = await api.post(`/internal-integrations/keys/${key.consumer}`)
      setNewKey({ ...res.data, label: key.label })
      await load()
    }, 'La génération de la clé a échoué')
  }

  const revoke = (key) => {
    if (
      !window.confirm(`Révoquer la clé « ${key.label} » ? Ce hub ne pourra plus lire vos données.`)
    )
      return
    run(async () => {
      const res = await api.delete(`/internal-integrations/keys/${key.consumer}`)
      apply(res.data)
      if (newKey?.consumer === key.consumer) setNewKey(null)
      setNotice(`Clé « ${key.label} » révoquée`)
    }, 'La révocation a échoué')
  }

  const copyKey = async () => {
    try {
      await navigator.clipboard.writeText(newKey.apiKey)
      setNotice('Copié dans le presse-papiers')
    } catch {
      setError('Impossible de copier — copiez le texte manuellement')
    }
  }

  const legacy = keys.find((k) => k.consumer === 'LEGACY')

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Intégration des hubs Praxio</h2>
          <p>
            Autorisez Compliance Engine, Logistix, Finance et le portail Praxio à lire vos données
          </p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      <div className="card">
        <div className="card-title">
          <h3>Qu'est-ce qui est partagé ?</h3>
        </div>
        <p className="muted">
          Chaque hub reçoit sa propre clé, jamais un accès direct à vos données, et n'accède qu'à ce
          dont il a besoin : Compliance Engine lit vos expéditions (factures douanières) et peut y
          créer des brouillons si vous l'autorisez ; Logistix (réception d'entrepôt) et Finance
          (reporting carbone) les lisent seulement ; le portail Praxio ne voit que des résumés
          d'activité et de facturation. Si votre abonnement est suspendu, ces accès sont gelés comme
          le reste de vos données.
        </p>
      </div>

      <div className="card">
        <div className="card-title">
          <h3>Statut du partage</h3>
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
                ? 'Les hubs disposant d’une clé ci-dessous peuvent lire vos données.'
                : "Aucun hub ne peut lire vos données tant que le partage n'est pas activé, même avec une clé."}
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
            <h3>Nouvelle clé « {newKey.label} »</h3>
            <span className="muted">Montrée une seule fois — notez-la maintenant</span>
          </div>
          <div className="form-field">
            <label>Clé d'intégration (X-Internal-Integration-Key)</label>
            <div className="copy-row">
              <code>{newKey.apiKey}</code>
              <button className="btn btn-outline btn-sm" onClick={copyKey}>
                Copier
              </button>
            </div>
          </div>
          <p className="muted">
            À coller dans <strong>{WHERE_TO_PASTE[newKey.consumer]}</strong>. Si vous la perdez,
            générez-en une nouvelle : l'ancienne cessera aussitôt de fonctionner.
          </p>
        </div>
      )}

      {!loading && (
        <div className="card">
          <div className="card-title">
            <h3>Clés par hub</h3>
          </div>
          {legacy && (
            <div className="alert">
              Une clé historique, partagée par tous les hubs, est encore active. Générez une clé
              pour chaque hub, collez-la dans ce hub, puis révoquez la clé historique.
            </div>
          )}
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Hub</th>
                  <th>Droits</th>
                  <th>Clé</th>
                  <th>Dernière utilisation</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {keys.map((k) => (
                  <tr key={k.consumer}>
                    <td>
                      <strong>{k.label}</strong>
                    </td>
                    <td>{k.scopes.map((s) => SCOPE_LABELS[s] || s).join(', ')}</td>
                    <td>
                      {k.configured ? (
                        <span className="badge badge-green">
                          {k.keyHint ? `…${k.keyHint}` : 'Active'}
                        </span>
                      ) : (
                        <span className="badge badge-gray">Aucune</span>
                      )}
                    </td>
                    <td>{fmtDate(k.lastUsedAt)}</td>
                    <td>
                      <div className="row-actions">
                        {k.consumer !== 'LEGACY' && (
                          <button
                            className="btn btn-outline btn-sm"
                            disabled={busy}
                            onClick={() => generate(k)}
                          >
                            {k.configured ? 'Remplacer' : 'Générer'}
                          </button>
                        )}
                        {k.configured && (
                          <button
                            className="btn btn-outline btn-sm"
                            disabled={busy}
                            onClick={() => revoke(k)}
                          >
                            Révoquer
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}
