import { useEffect, useRef, useState } from 'react'
import api from '../services/api'

const DEFAULT_BRANDING = {
  primaryColor: '#7c3aed',
  secondaryColor: '#f59e0b',
  portalTitle: 'Portail Client',
  welcomeMessage: 'Bienvenue sur votre portail de suivi de livraisons',
  footerText: '© 2026 Fleet Hub — Tous droits réservés',
  customCssEnabled: false,
  customCss: ''
}

export default function BrandingPortal() {
  const fileInputRef = useRef(null)
  const [settings, setSettings] = useState(DEFAULT_BRANDING)
  const [logoFile, setLogoFile] = useState(null)
  const [logoPreview, setLogoPreview] = useState(null)
  const [hasChanges, setHasChanges] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const applyData = (data) => {
    if (!data) return
    setSettings({
      primaryColor: data.primaryColor || DEFAULT_BRANDING.primaryColor,
      secondaryColor: data.secondaryColor || DEFAULT_BRANDING.secondaryColor,
      portalTitle: data.portalTitle || DEFAULT_BRANDING.portalTitle,
      welcomeMessage: data.welcomeMessage || DEFAULT_BRANDING.welcomeMessage,
      footerText: data.footerText || DEFAULT_BRANDING.footerText,
      customDomain: data.customDomain || '',
      customCssEnabled: data.customCssEnabled || false,
      customCss: data.customCss || '',
      logoUrl: data.logoUrl
    })
    if (data.logoUrl) setLogoPreview(data.logoUrl)
  }

  useEffect(() => {
    api
      .get('/branding')
      .then((res) => applyData(res.data))
      .catch(() => setError('Impossible de charger la configuration'))
      .finally(() => setLoading(false))
  }, [])

  const updateField = (key, value) => {
    setSettings((prev) => ({ ...prev, [key]: value }))
    setHasChanges(true)
  }

  const handleLogoChange = (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    setLogoFile(file)
    const reader = new FileReader()
    reader.onload = (ev) => setLogoPreview(ev.target?.result)
    reader.readAsDataURL(file)
  }

  const uploadLogo = async () => {
    if (!logoFile) return
    const fd = new FormData()
    fd.append('file', logoFile)
    const res = await api.post('/branding/logo', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    setLogoPreview(res.data?.logoUrl || logoPreview)
    setLogoFile(null)
    return res
  }

  const handleSave = async () => {
    setSaving(true)
    setError('')
    setNotice('')
    try {
      if (logoFile) await uploadLogo()
      await api.put('/branding', settings)
      setNotice('Configuration du portail sauvegardée')
      setHasChanges(false)
    } catch (err) {
      setError(err.response?.data?.message || 'Erreur lors de la sauvegarde')
    } finally {
      setSaving(false)
    }
  }

  const handleReset = () => {
    api
      .get('/branding')
      .then((res) => {
        applyData(res.data)
        setLogoFile(null)
        setHasChanges(false)
        setNotice('Modifications annulées')
      })
      .catch(() => {})
  }

  if (loading) {
    return (
      <div className="loading-spinner" aria-label="Chargement…">
        Chargement…
      </div>
    )
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Portail white-label</h2>
          <p>Personnalisez l'apparence de votre portail client</p>
        </div>
        <div className="header-actions">
          {hasChanges && (
            <button className="btn btn-outline" disabled={saving} onClick={handleReset}>
              Annuler
            </button>
          )}
          <button className="btn btn-primary" disabled={saving} onClick={handleSave}>
            {saving ? 'Sauvegarde…' : '💾 Sauvegarder'}
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      <div className="dashboard-grid">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div className="card">
            <div className="card-title">
              <h3>🖼️ Logo de l'entreprise</h3>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
              <div
                style={{
                  width: 90,
                  height: 90,
                  border: '2px dashed var(--border-strong)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                  overflow: 'hidden'
                }}
              >
                {logoPreview ? (
                  <img src={logoPreview} alt="Logo" style={{ width: '100%', height: '100%', objectFit: 'contain' }} />
                ) : (
                  <span style={{ fontSize: 24 }}>🖼️</span>
                )}
              </div>
              <div>
                <input ref={fileInputRef} type="file" accept="image/*" onChange={handleLogoChange} style={{ display: 'none' }} />
                <button type="button" className="btn btn-outline btn-sm" onClick={() => fileInputRef.current?.click()}>
                  Choisir un fichier
                </button>
                <p className="muted" style={{ fontSize: 11, marginTop: 6 }}>PNG, JPG ou SVG. Max 2 Mo.</p>
                {logoFile && (
                  <button className="link" style={{ background: 'none', border: 'none', cursor: 'pointer', marginTop: 4 }} onClick={uploadLogo}>
                    Téléverser le logo
                  </button>
                )}
              </div>
            </div>
          </div>

          <div className="card">
            <div className="card-title">
              <h3>🎨 Couleurs</h3>
            </div>
            <div className="form-row">
              <div className="form-field">
                <label>Couleur primaire</label>
                <div style={{ display: 'flex', gap: 10 }}>
                  <input type="color" value={settings.primaryColor} onChange={(e) => updateField('primaryColor', e.target.value)} style={{ width: 44, padding: 2 }} />
                  <input type="text" value={settings.primaryColor} onChange={(e) => updateField('primaryColor', e.target.value)} style={{ flex: 1, fontFamily: 'var(--font-mono)' }} />
                </div>
              </div>
              <div className="form-field">
                <label>Couleur secondaire</label>
                <div style={{ display: 'flex', gap: 10 }}>
                  <input type="color" value={settings.secondaryColor} onChange={(e) => updateField('secondaryColor', e.target.value)} style={{ width: 44, padding: 2 }} />
                  <input type="text" value={settings.secondaryColor} onChange={(e) => updateField('secondaryColor', e.target.value)} style={{ flex: 1, fontFamily: 'var(--font-mono)' }} />
                </div>
              </div>
            </div>
          </div>

          <div className="card">
            <div className="card-title">
              <h3>🔤 Contenu textuel</h3>
            </div>
            <div className="form-field">
              <label>Titre du portail</label>
              <input type="text" value={settings.portalTitle} onChange={(e) => updateField('portalTitle', e.target.value)} />
            </div>
            <div className="form-field" style={{ marginTop: 12 }}>
              <label>Message de bienvenue</label>
              <textarea rows={3} value={settings.welcomeMessage} onChange={(e) => updateField('welcomeMessage', e.target.value)} />
            </div>
            <div className="form-field" style={{ marginTop: 12 }}>
              <label>Texte du pied de page</label>
              <input type="text" value={settings.footerText} onChange={(e) => updateField('footerText', e.target.value)} />
            </div>
          </div>

          <div className="card">
            <div className="card-title">
              <h3>🌐 Domaine personnalisé</h3>
            </div>
            <div className="form-field">
              <label>Domaine (optionnel)</label>
              <input
                type="text"
                value={settings.customDomain || ''}
                onChange={(e) => updateField('customDomain', e.target.value)}
                placeholder="portail.monentreprise.com"
              />
              <p className="muted" style={{ fontSize: 11 }}>Configurez votre enregistrement DNS CNAME pointant vers votre portail Fleet Hub.</p>
            </div>
          </div>

          <div className="card">
            <div className="card-title">
              <h3>🧩 CSS personnalisé</h3>
            </div>
            <div className="form-checkbox-row" style={{ paddingTop: 0 }}>
              <input
                type="checkbox"
                checked={settings.customCssEnabled}
                onChange={(e) => updateField('customCssEnabled', e.target.checked)}
                id="custom-css-toggle"
              />
              <label htmlFor="custom-css-toggle">Activer le CSS personnalisé</label>
            </div>
            {settings.customCssEnabled && (
              <textarea
                rows={8}
                style={{ fontFamily: 'var(--font-mono)', marginTop: 10 }}
                value={settings.customCss || ''}
                onChange={(e) => updateField('customCss', e.target.value)}
                placeholder="/* Vos styles CSS personnalisés */"
              />
            )}
          </div>
        </div>

        <div>
          <div className="card" style={{ position: 'sticky', top: 20 }}>
            <div className="card-title">
              <h3>👁️ Aperçu</h3>
            </div>
            <div style={{ border: `1px solid ${settings.primaryColor}`, overflow: 'hidden' }}>
              <div style={{ padding: '18px 20px', display: 'flex', alignItems: 'center', gap: 10, background: settings.primaryColor }}>
                <div style={{ width: 28, height: 28, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontWeight: 700, fontSize: 11, background: 'rgba(255,255,255,0.2)' }}>
                  {logoPreview ? <img src={logoPreview} alt="" style={{ width: '100%', height: '100%', objectFit: 'contain' }} /> : 'FH'}
                </div>
                <span style={{ color: '#fff', fontWeight: 700, fontSize: 13 }}>{settings.portalTitle || 'Portail Client'}</span>
              </div>
              <div style={{ padding: 20, background: 'var(--card)', display: 'flex', flexDirection: 'column', gap: 12 }}>
                <h4 style={{ color: settings.primaryColor, margin: 0 }}>{settings.welcomeMessage || 'Bienvenue sur votre portail'}</h4>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                  <div style={{ padding: 10, background: `${settings.primaryColor}1A`, borderLeft: `3px solid ${settings.primaryColor}` }}>
                    <p style={{ margin: 0, fontSize: 11, fontWeight: 700, color: settings.primaryColor }}>Expéditions</p>
                    <p style={{ margin: 0, fontSize: 22, fontWeight: 800 }}>12</p>
                  </div>
                  <div style={{ padding: 10, background: `${settings.secondaryColor}1A`, borderLeft: `3px solid ${settings.secondaryColor}` }}>
                    <p style={{ margin: 0, fontSize: 11, fontWeight: 700, color: settings.secondaryColor }}>En transit</p>
                    <p style={{ margin: 0, fontSize: 22, fontWeight: 800 }}>5</p>
                  </div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: 10, border: '1px solid var(--border)' }}>
                  <span style={{ width: 8, height: 8, borderRadius: '50%', background: settings.primaryColor }} />
                  <div style={{ flex: 1 }}>
                    <p style={{ margin: 0, fontSize: 12, fontWeight: 650 }}>EXP-2026-0042</p>
                    <p className="muted" style={{ margin: 0, fontSize: 11 }}>Paris → New York</p>
                  </div>
                  <span style={{ fontSize: 11, fontWeight: 650, padding: '2px 8px', background: `${settings.secondaryColor}30`, color: settings.secondaryColor }}>En cours</span>
                </div>
              </div>
              <div style={{ padding: '10px 20px', textAlign: 'center', fontSize: 11, background: `${settings.primaryColor}14`, color: settings.primaryColor }}>
                {settings.footerText || '© 2026 Fleet Hub'}
              </div>
            </div>
            <p className="muted" style={{ fontSize: 11, textAlign: 'center', marginTop: 10 }}>Aperçu dynamique basé sur vos paramètres actuels</p>
          </div>
        </div>
      </div>
    </div>
  )
}
