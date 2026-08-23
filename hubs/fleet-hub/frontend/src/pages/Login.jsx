import { useState } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [totpCode, setTotpCode] = useState('')
  const [totpRequired, setTotpRequired] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const from = location.state?.from?.pathname || '/'

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const result = await login(username, password, totpCode || undefined)
      // 2FA requis : afficher le champ TOTP
      if (result.totpRequired) {
        setTotpRequired(true)
        setLoading(false)
        return
      }
      const fallback = result.role === 'SAAS_ADMIN' ? '/admin' : '/'
      navigate(from && from !== '/' ? from : fallback, { replace: true })
    } catch (err) {
      if (totpRequired && err.response?.status === 401) {
        setError('Code TOTP invalide')
      } else {
        setError(err.response?.status === 403 || err.response?.status === 401
          ? 'Identifiants invalides'
          : 'Erreur de connexion au serveur')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">🚛</div>
        <h1>Fleet Hub</h1>
        <p className="login-sub">Gestion de flotte - KPIs Chauffeur x Camion</p>
        <form onSubmit={handleSubmit}>
          {!totpRequired ? (
            <>
              <label>
                Utilisateur
                <input
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoComplete="username"
                />
              </label>
              <label>
                Mot de passe
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                />
              </label>
            </>
          ) : (
            <div className="totp-notice">
              <p>Double authentification activée</p>
              <label>
                Code TOTP (6 chiffres)
                <input
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]{6}"
                  maxLength={6}
                  value={totpCode}
                  onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, ''))}
                  autoFocus
                  autoComplete="one-time-code"
                />
              </label>
            </div>
          )}
          <p className="login-forgot">
            <Link to="/forgot-password">Mot de passe oublié ?</Link>
          </p>
          {error && <div className="alert alert-error">{error}</div>}
          <button className="btn btn-primary btn-block" disabled={loading}>
            {loading ? 'Connexion…' : totpRequired ? 'Vérifier le code' : 'Se connecter'}
          </button>
        </form>
        <p className="login-hint">
          Pas de compte ? <Link to="/register">Créer ma société</Link>
        </p>
        <p className="login-hint legal-links">
          <Link to="/legal/terms">CGU</Link>
          <span>·</span>
          <Link to="/legal/privacy">Confidentialité</Link>
          <span>·</span>
          <span className="muted">Données hébergées en UE (RGPD)</span>
        </p>
      </div>
    </div>
  )
}
