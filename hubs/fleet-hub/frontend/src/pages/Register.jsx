import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { ACTIVITIES, activityOf, chosenActivity, rememberActivity } from '../fleet'

export default function Register() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const initial = chosenActivity(location?.search) || ACTIVITIES[0]
  const [form, setForm] = useState({
    companyName: '',
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    confirm: '',
    fleetProfile: initial.value
  })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const activity = activityOf(form.fleetProfile)
  const medical = activity.value === 'COLLECTE_SANTE'

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    if (!form.companyName || !form.firstName || !form.lastName || !form.email || !form.password) {
      setError('Veuillez remplir tous les champs')
      return
    }
    if (form.password.length < 8) {
      setError('Le mot de passe doit contenir au moins 8 caractères')
      return
    }
    if (form.password !== form.confirm) {
      setError('Les mots de passe ne correspondent pas')
      return
    }
    setLoading(true)
    try {
      await register({
        companyName: form.companyName,
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email,
        password: form.password,
        fleetProfile: form.fleetProfile
      })
      rememberActivity(activity.slug)
      navigate('/', { replace: true })
    } catch (err) {
      setError(
        err.response?.status === 409
          ? 'Un compte existe déjà avec cet email'
          : 'Erreur lors de la création du compte'
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={`login-page activity-${activity.slug}`}>
      <div className="login-card">
        <div className="login-logo">{activity.icon}</div>
        <h1>Fleet Hub</h1>
        <p className="login-sub">Créez votre espace {activity.label.toLowerCase()} en 2 minutes</p>
        <form onSubmit={handleSubmit}>
          <fieldset className="activity-picker">
            <legend>Votre activité</legend>
            {ACTIVITIES.map((a) => (
              <label
                key={a.value}
                className={'activity-choice' + (form.fleetProfile === a.value ? ' active' : '')}
              >
                <input
                  type="radio"
                  name="fleetProfile"
                  value={a.value}
                  checked={form.fleetProfile === a.value}
                  onChange={set('fleetProfile')}
                />
                <span aria-hidden="true">{a.icon}</span>
                <span>{a.label}</span>
              </label>
            ))}
          </fieldset>
          <label>
            Nom de la société
            <input
              type="text"
              value={form.companyName}
              onChange={set('companyName')}
              placeholder={
                medical ? 'Bio Coursiers, Navette Labo Express…' : 'Transports Martin SAS…'
              }
              autoComplete="organization"
            />
          </label>
          <div className="form-row">
            <label>
              Prénom
              <input
                type="text"
                value={form.firstName}
                onChange={set('firstName')}
                autoComplete="given-name"
              />
            </label>
            <label>
              Nom
              <input
                type="text"
                value={form.lastName}
                onChange={set('lastName')}
                autoComplete="family-name"
              />
            </label>
          </div>
          <label>
            Email (identifiant)
            <input type="email" value={form.email} onChange={set('email')} autoComplete="email" />
          </label>
          <label>
            Mot de passe
            <input
              type="password"
              value={form.password}
              onChange={set('password')}
              autoComplete="new-password"
            />
          </label>
          <label>
            Confirmer le mot de passe
            <input
              type="password"
              value={form.confirm}
              onChange={set('confirm')}
              autoComplete="new-password"
            />
          </label>
          {medical && (
            <p className="login-activity">
              Traçabilité par comptage des produits (sang, PCR, urines…) : aucune donnée patient
              n'est saisie ni stockée dans Fleet Hub.
            </p>
          )}
          {error && <div className="alert alert-error">{error}</div>}
          <button className="btn btn-primary btn-block" disabled={loading}>
            {loading ? 'Création…' : 'Créer mon compte'}
          </button>
        </form>
        <p className="login-hint">Essai gratuit 14 jours · Données hébergées en France (RGPD)</p>
        <p className="login-hint">
          En créant votre compte, vous acceptez nos <Link to="/legal/terms">CGU</Link> et notre{' '}
          <Link to="/legal/privacy">politique de confidentialité</Link>.
        </p>
        <p className="login-hint">
          Déjà un compte ? <Link to={`/login?activite=${activity.slug}`}>Se connecter</Link>
          {' · '}
          <Link to="/portail">Portail</Link>
        </p>
      </div>
    </div>
  )
}
