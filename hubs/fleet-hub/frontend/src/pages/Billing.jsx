import { useEffect, useState } from 'react'
import api from '../services/api'
import { useAuth } from '../context/AuthContext'

const PLANS = [
  { key: 'TRIAL', name: 'Essai', price: '0 €', period: '14 jours', desc: 'Pour découvrir la plateforme', limits: '10 véh / 5 chauf' },
  { key: 'STARTER', name: 'Starter', price: '49 €', period: '/ mois', desc: 'Pour les petites flottes', limits: '25 véh / 10 chauf' },
  { key: 'PRO', name: 'Pro', price: '99 €', period: '/ mois', desc: 'Le meilleur rapport qualité / prix', limits: '100 véh / 50 chauf' },
  { key: 'ENTERPRISE', name: 'Enterprise', price: 'Sur devis', period: '', desc: 'Flottes illimitées et accompagnement', limits: 'Illimité' }
]

const STATUS_LABEL = {
  ACTIVE: 'Active',
  TRIAL: 'Essai',
  SUSPENDED: 'Suspendue',
  CANCELLED: 'Annulée'
}

const STATUS_CLASS = {
  ACTIVE: 'badge-green',
  TRIAL: 'badge-blue',
  SUSPENDED: 'badge-red',
  CANCELLED: 'badge-gray'
}

const fmtDate = (s) => (s ? new Date(s).toLocaleDateString('fr-FR') : '—')

export default function Billing() {
  const { user } = useAuth()
  const [status, setStatus] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState('')

  const load = () =>
    api
      .get('/billing/status')
      .then((res) => setStatus(res.data))
      .catch(() => setError('Impossible de charger le statut de l’abonnement'))
      .finally(() => setLoading(false))

  useEffect(() => {
    load()
  }, [])

  const run = async (key, url, body) => {
    setError('')
    setNotice('')
    setBusy(key)
    try {
      const res = await api.post(url, body)
      if (res.data && res.data.url) {
        window.location.href = res.data.url
      } else {
        setNotice('Plan mis à jour')
        await load()
      }
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || 'L’opération a échoué')
    } finally {
      setBusy('')
    }
  }

  const subscribe = (plan) => run(`checkout-${plan}`, '/billing/checkout', { plan })
  const portal = () => run('portal', '/billing/portal')

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Abonnement</h2>
          <p>Gérez votre plan et vos informations de facturation</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}
      {user?.subscriptionActive === false && (
        <div className="alert alert-error">
          {user.companyStatus === 'SUSPENDED'
            ? 'Votre abonnement est suspendu. Régularisez votre paiement pour réactiver l’accès à vos données.'
            : 'Votre essai gratuit est terminé. Choisissez un plan ci-dessous pour continuer à utiliser Fleet Hub.'}
        </div>
      )}
      {loading && <p className="muted">Chargement…</p>}

      {status && (
        <>
          <div className="card">
            <div className="card-title">
              <h3>Abonnement actuel</h3>
              <span className={`badge ${STATUS_CLASS[status.status] || 'badge-gray'}`}>
                {STATUS_LABEL[status.status] || status.status}
              </span>
            </div>
            <div className="data-grid">
              <div className="form-field">
                <label>Plan</label>
                <input type="text" value={status.plan} readOnly disabled />
              </div>
              <div className="form-field">
                <label>Fin de la période d’essai</label>
                <input type="text" value={fmtDate(status.trialEndsAt)} readOnly disabled />
              </div>
              <div className="form-field">
                <label>Limites</label>
                <input type="text" value={`${status.maxVehicles} véhicules · ${status.maxDrivers} chauffeurs`} readOnly disabled />
              </div>
              <div className="form-field">
                <label>Référence d’abonnement</label>
                <input type="text" value={status.subscriptionId || '—'} readOnly disabled />
              </div>
            </div>
          </div>

          {status.status !== 'CANCELLED' && status.status !== 'SUSPENDED' && (
            <button className="btn btn-outline" disabled={busy === 'portal'} onClick={portal}>
              {busy === 'portal' ? 'Redirection…' : 'Gérer les paiements (portail Stripe)'}
            </button>
          )}

          <div className="card">
            <div className="card-title">
              <h3>Choisir un plan</h3>
              <span className="muted">
                {status.stripeConfigured
                  ? 'Paiement sécurisé par Stripe'
                  : 'La facturation en ligne n’est pas encore activée sur cette plateforme'}
              </span>
            </div>
            <div className="data-grid">
              {PLANS.filter((p) => p.key !== 'TRIAL').map((p) => {
                const isCurrent = status.plan === p.key
                return (
                  <div key={p.key} className="form-field">
                    <label>{p.name}</label>
                    <button
                      className={'btn btn-block ' + (isCurrent ? 'btn-outline' : 'btn-primary')}
                      disabled={busy !== '' || isCurrent}
                      onClick={() => subscribe(p.key)}
                    >
                      {isCurrent
                        ? 'Plan actuel'
                        : busy === `checkout-${p.key}`
                          ? 'Redirection…'
                          : `Passer à ${p.name} — ${p.price} ${p.period}`}
                    </button>
                    <p className="muted">{p.desc}</p>
                  </div>
                )
              })}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
