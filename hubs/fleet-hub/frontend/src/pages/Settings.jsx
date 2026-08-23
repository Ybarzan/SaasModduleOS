import { useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import api from '../services/api'

export default function Settings() {
  const { user, logout } = useAuth()
  const [totpEnabled, setTotpEnabled] = useState(user?.totpEnabled || false)
  const [step, setStep] = useState('idle')
  const [otpUri, setOtpUri] = useState('')
  const [secret, setSecret] = useState('')
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const fetchStatus = async () => {
    try {
      const res = await api.get('/users/2fa/status')
      setTotpEnabled(res.data.totpEnabled)
    } catch {
      setTotpEnabled(user?.totpEnabled || false)
    }
  }

  useEffect(() => {
    fetchStatus()
  }, [])

  const handleSetup = async () => {
    setError('')
    setLoading(true)
    try {
      const res = await api.post('/users/2fa/setup')
      setOtpUri(res.data.otpauthUri)
      setSecret(res.data.secret)
      setStep('scan')
    } catch (err) {
      setError(err.response?.data?.message || 'Erreur lors de l\'initialisation')
    } finally {
      setLoading(false)
    }
  }

  const handleEnable = async () => {
    setError('')
    setLoading(true)
    try {
      await api.post('/users/2fa/enable', { code })
      setTotpEnabled(true)
      setStep('idle')
      setCode('')
      setOtpUri('')
      setSecret('')
      const updatedUser = { ...user, totpEnabled: true }
      localStorage.setItem('fh_user', JSON.stringify(updatedUser))
    } catch (err) {
      setError(err.response?.data?.message || 'Code invalide')
    } finally {
      setLoading(false)
    }
  }

  const handleDisable = async () => {
    setError('')
    setLoading(true)
    try {
      await api.post('/users/2fa/disable', { code })
      setTotpEnabled(false)
      setStep('idle')
      setCode('')
      const updatedUser = { ...user, totpEnabled: false }
      localStorage.setItem('fh_user', JSON.stringify(updatedUser))
    } catch (err) {
      setError(err.response?.data?.message || 'Code invalide')
    } finally {
      setLoading(false)
    }
  }

  const qrCodeUrl = otpUri
    ? `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(otpUri)}`
    : ''

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Paramètres</h2>
          <p>Sécurité et configuration du compte</p>
        </div>
      </div>

      <div className="card">
        <div className="card-title">
          <h3>Double authentification (2FA)</h3>
          <span className={'badge ' + (totpEnabled ? 'badge-green' : 'badge-gray')}>
            {totpEnabled ? 'Activée' : 'Désactivée'}
          </span>
        </div>
        <p className="muted" style={{ marginBottom: '1rem' }}>
          Protégez votre compte avec une seconde couche d'authentification via une application
          comme Google Authenticator, Authy ou Microsoft Authenticator.
        </p>

        {error && <div className="alert alert-error">{error}</div>}

        {step === 'idle' && !totpEnabled && (
          <button className="btn btn-primary" onClick={handleSetup} disabled={loading}>
            {loading ? 'Chargement...' : 'Activer la 2FA'}
          </button>
        )}

        {step === 'idle' && totpEnabled && (
          <div>
            <p style={{ marginBottom: '0.75rem', color: 'var(--color-success)' }}>
              La double authentification est active sur votre compte.
            </p>
            <button
              className="btn btn-danger"
              onClick={() => setStep('disable')}
              disabled={loading}
            >
              Désactiver la 2FA
            </button>
          </div>
        )}

        {step === 'scan' && (
          <div style={{ textAlign: 'center' }}>
            <p style={{ marginBottom: '1rem' }}>
              Scannez ce QR code avec votre application d'authentification :
            </p>
            {qrCodeUrl && (
              <img
                src={qrCodeUrl}
                alt="QR Code 2FA"
                width="200"
                height="200"
                style={{ borderRadius: '8px', marginBottom: '1rem' }}
              />
            )}
            <details style={{ marginBottom: '1rem' }}>
              <summary className="muted" style={{ cursor: 'pointer' }}>
                Saisir le code manuellement
              </summary>
              <code
                style={{
                  display: 'block',
                  marginTop: '0.5rem',
                  padding: '0.5rem',
                  background: 'var(--bg-soft)',
                  borderRadius: '6px',
                  fontSize: '0.85rem',
                  wordBreak: 'break-all'
                }}
              >
                {secret}
              </code>
            </details>
            <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'center', alignItems: 'center' }}>
              <input
                className="form-input"
                placeholder="Code à 6 chiffres"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                maxLength={6}
                style={{ maxWidth: '160px', textAlign: 'center', fontSize: '1.2rem', letterSpacing: '0.3em' }}
                onKeyDown={(e) => e.key === 'Enter' && handleEnable()}
              />
              <button className="btn btn-primary" onClick={handleEnable} disabled={loading || code.length < 6}>
                Vérifier et activer
              </button>
            </div>
            <button
              className="btn btn-outline"
              style={{ marginTop: '1rem' }}
              onClick={() => { setStep('idle'); setCode(''); setOtpUri(''); setSecret('') }}
            >
              Annuler
            </button>
          </div>
        )}

        {step === 'disable' && (
          <div style={{ textAlign: 'center' }}>
            <p style={{ marginBottom: '1rem' }}>
              Entrez un code TOTP pour confirmer la désactivation :
            </p>
            <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'center', alignItems: 'center' }}>
              <input
                className="form-input"
                placeholder="Code à 6 chiffres"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                maxLength={6}
                style={{ maxWidth: '160px', textAlign: 'center', fontSize: '1.2rem', letterSpacing: '0.3em' }}
                onKeyDown={(e) => e.key === 'Enter' && handleDisable()}
              />
              <button className="btn btn-danger" onClick={handleDisable} disabled={loading || code.length < 6}>
                Désactiver
              </button>
            </div>
            <button
              className="btn btn-outline"
              style={{ marginTop: '1rem' }}
              onClick={() => { setStep('idle'); setCode('') }}
            >
              Annuler
            </button>
          </div>
        )}
      </div>

      <div className="card" style={{ marginTop: '1.5rem' }}>
        <div className="card-title">
          <h3>Informations du compte</h3>
        </div>
        <div className="data-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))' }}>
          <div className="stat-card">
            <span className="stat-label">Utilisateur</span>
            <span className="stat-value" style={{ fontSize: '1rem' }}>{user?.displayName || user?.username}</span>
          </div>
          <div className="stat-card">
            <span className="stat-label">Email</span>
            <span className="stat-value" style={{ fontSize: '1rem' }}>{user?.email || '—'}</span>
          </div>
          <div className="stat-card">
            <span className="stat-label">Rôle</span>
            <span className="stat-value" style={{ fontSize: '1rem' }}>{user?.role}</span>
          </div>
          <div className="stat-card">
            <span className="stat-label">Société</span>
            <span className="stat-value" style={{ fontSize: '1rem' }}>{user?.companyName || '—'}</span>
          </div>
        </div>
      </div>
    </div>
  )
}
