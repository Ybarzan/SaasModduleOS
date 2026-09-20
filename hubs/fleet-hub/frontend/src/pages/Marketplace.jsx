import { useEffect, useState } from 'react'
import api from '../services/api'

export default function Marketplace() {
  const [optIn, setOptIn] = useState(false)
  const [newKey, setNewKey] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = () =>
    api
      .get('/marketplace/settings')
      .then((res) => setOptIn(res.data.marketplaceOptIn))
      .catch(() => setError('Impossible de charger le statut du partage'))
      .finally(() => setLoading(false))

  useEffect(() => {
    load()
  }, [])

  const activate = async () => {
    setError('')
    setNotice('')
    setBusy(true)
    try {
      const res = await api.post('/marketplace/opt-in')
      setOptIn(true)
      setNewKey(res.data.marketplaceApiKey)
    } catch (err) {
      setError(err.response?.data?.message || "L'activation a échoué")
    } finally {
      setBusy(false)
    }
  }

  const deactivate = async () => {
    if (!window.confirm('Désactiver le partage ? Votre flotte disparaîtra du tableau FleetMarket.')) return
    setError('')
    setNotice('')
    setBusy(true)
    try {
      await api.post('/marketplace/opt-out')
      setOptIn(false)
      setNewKey(null)
      setNotice('Partage désactivé')
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
          <h2>Marketplace</h2>
          <p>Publiez la disponibilité de votre flotte sur FleetMarket, la bourse de fret entre PME</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      <div className="card">
        <div className="card-title">
          <h3>Qu'est-ce qui est partagé ?</h3>
        </div>
        <p className="muted">
          Une fois activé, FleetMarket peut consulter — via une clé propre à votre société, jamais
          un accès direct à vos données — la liste de vos camions actifs qui ne sont pas
          actuellement en conduite, et un score de conformité agrégé sur 30 jours (tachygraphe,
          561/2006). <strong>Aucune donnée nominative de chauffeur n'est jamais transmise.</strong>{' '}
          Ce score est le badge de confiance affiché à vos concurrents et aux donneurs d'ordre sur
          FleetMarket.
        </p>
      </div>

      <div className="card">
        <div className="card-title">
          <h3>Statut du partage</h3>
          {!loading && (
            <span className={`badge ${optIn ? 'badge-green' : 'badge-gray'}`}>
              {optIn ? 'Actif' : 'Désactivé'}
            </span>
          )}
        </div>

        {loading ? (
          <p className="muted table-empty">Chargement…</p>
        ) : (
          <>
            <p className="muted">
              {optIn
                ? 'Votre flotte est visible sur FleetMarket pour les transporteurs de votre réseau.'
                : "Votre flotte n'apparaît nulle part sur FleetMarket tant que le partage n'est pas activé."}
            </p>
            {optIn ? (
              <button className="btn btn-outline" disabled={busy} onClick={deactivate}>
                {busy ? 'Désactivation…' : 'Désactiver le partage'}
              </button>
            ) : (
              <button className="btn btn-primary" disabled={busy} onClick={activate}>
                {busy ? 'Activation…' : 'Activer le partage'}
              </button>
            )}
          </>
        )}
      </div>

      {newKey && (
        <div className="card">
          <div className="card-title">
            <h3>Votre clé FleetMarket</h3>
            <span className="muted">Montrée une seule fois — notez-la maintenant</span>
          </div>
          <div className="form-field">
            <label>Clé d'inscription transporteur (X-Marketplace-Key)</label>
            <div className="copy-row">
              <code>{newKey}</code>
              <button className="btn btn-outline btn-sm" onClick={copyKey}>
                Copier
              </button>
            </div>
          </div>
          <p className="muted">
            Utilisez cette clé pour inscrire votre société comme transporteur sur{' '}
            <strong>FleetMarket</strong> (page « Transporteur » de l'inscription). Si vous la
            perdez, désactivez puis réactivez le partage ci-dessus pour la retrouver — la clé ne
            change pas.
          </p>
        </div>
      )}
    </div>
  )
}
