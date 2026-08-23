import { useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../services/api'

export default function ForgotPassword() {
  const [username, setUsername] = useState('')
  const [error, setError] = useState('')
  const [sent, setSent] = useState(false)
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await api.post('/auth/forgot-password', { username: username.trim() })
      setSent(true)
    } catch (err) {
      setError(err.response?.data?.message || 'La demande a échoué, réessayez plus tard')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">🚛</div>
        <h1>Fleet Hub</h1>
        {sent ? (
          <>
            <p className="login-sub">Vérifiez votre boîte mail</p>
            <p className="login-hint">
              Si un compte existe pour <strong>{username.trim()}</strong>, un email avec un lien de
              réinitialisation (valable 1 heure) vient d'être envoyé.
            </p>
            <Link to="/login" className="btn btn-primary btn-block">
              Retour à la connexion
            </Link>
          </>
        ) : (
          <>
            <p className="login-sub">Recevez un lien pour choisir un nouveau mot de passe</p>
            <form onSubmit={handleSubmit}>
              <label>
                Identifiant (email)
                <input
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoComplete="username"
                  autoFocus
                />
              </label>
              {error && <div className="alert alert-error">{error}</div>}
              <button className="btn btn-primary btn-block" disabled={loading || !username.trim()}>
                {loading ? 'Envoi…' : 'Envoyer le lien'}
              </button>
            </form>
            <p className="login-hint">
              <Link to="/login">← Retour à la connexion</Link>
            </p>
          </>
        )}
      </div>
    </div>
  )
}
