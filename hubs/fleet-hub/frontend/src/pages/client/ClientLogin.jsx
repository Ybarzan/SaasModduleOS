import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../../services/api'
import './client.css'

export default function ClientLogin() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await api.post('/client/auth/login', { email, password })
      const { token, clientId, email: em, fullName, companyId } = res.data
      localStorage.setItem('fh_client_token', token)
      localStorage.setItem(
        'fh_client_user',
        JSON.stringify({ id: clientId, email: em, fullName, companyId })
      )
      navigate('/client/dashboard')
    } catch (err) {
      setError(err.response?.data?.message || 'Erreur de connexion')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="cp-page cp-login-shell">
      <div className="cp-login-box">
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <div
            className="cp-brand-icon"
            style={{ margin: '0 auto 12px', width: 56, height: 56, fontSize: 28 }}
          >
            📦
          </div>
          <h2>Espace client</h2>
          <p className="muted">Connectez-vous pour suivre vos expéditions</p>
        </div>

        <div className="card">
          {error && <div className="alert alert-error">{error}</div>}
          <form onSubmit={handleSubmit}>
            <div className="form-field">
              <label>Email</label>
              <input
                type="email"
                value={email}
                required
                onChange={(e) => setEmail(e.target.value)}
                placeholder="client@entreprise.com"
              />
            </div>
            <div className="form-field" style={{ marginTop: 14 }}>
              <label>Mot de passe</label>
              <input
                type="password"
                value={password}
                required
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            <div className="form-actions">
              <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
                {loading ? 'Connexion…' : 'Se connecter'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}
