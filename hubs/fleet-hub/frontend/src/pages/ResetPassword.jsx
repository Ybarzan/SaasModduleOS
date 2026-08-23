import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import api from '../services/api'

export default function ResetPassword() {
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const [form, setForm] = useState({ password: '', confirm: '' })
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    if (!token) {
      setError('Ce lien est invalide : aucun jeton de réinitialisation n’a été fourni.')
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
      await api.post('/auth/reset-password', { token, password: form.password })
      setSuccess(true)
    } catch (err) {
      setError(err.response?.data?.message || 'La réinitialisation a échoué')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">🚛</div>
        <h1>Fleet Hub</h1>
        {success ? (
          <>
            <p className="login-sub">Mot de passe mis à jour !</p>
            <Link to="/login" className="btn btn-primary btn-block">
              Se connecter
            </Link>
          </>
        ) : (
          <>
            <p className="login-sub">Choisissez votre nouveau mot de passe</p>
            <form onSubmit={handleSubmit}>
              <label>
                Nouveau mot de passe
                <input
                  type="password"
                  value={form.password}
                  onChange={set('password')}
                  autoComplete="new-password"
                  placeholder="8 caractères minimum"
                  autoFocus
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
              {error && <div className="alert alert-error">{error}</div>}
              <button className="btn btn-primary btn-block" disabled={loading}>
                {loading ? 'Enregistrement…' : 'Réinitialiser le mot de passe'}
              </button>
            </form>
            <p className="login-hint">
              Lien expiré ? <Link to="/forgot-password">Demandez-en un nouveau</Link>
            </p>
          </>
        )}
      </div>
    </div>
  )
}
