import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import api from '../../services/api'
import { useAuth } from '../../context/AuthContext'
import './pointage.css'

const CONTINUOUS_LIMIT_SECONDS = 4.5 * 3600 // Art. 7, règlement (CE) 561/2006

function fmt(totalSeconds) {
  const s = Math.max(0, Math.round(totalSeconds))
  const h = String(Math.floor(s / 3600)).padStart(2, '0')
  const m = String(Math.floor((s % 3600) / 60)).padStart(2, '0')
  const sec = String(s % 60).padStart(2, '0')
  return `${h}:${m}:${sec}`
}

const fmtTime = (iso) => (iso ? new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }) : '—')

const EVENT_LABEL = {
  DEBUT: 'Début de service',
  PAUSE_DEBUT: 'Début de pause',
  PAUSE_FIN: 'Reprise',
  FIN: 'Fin de service'
}

export default function PointagePortal() {
  const { accessCode: urlCode } = useParams()
  const navigate = useNavigate()
  const { user, login, logout } = useAuth()
  const isChauffeur = user?.role === 'CHAUFFEUR'

  useEffect(() => {
    // Titre distinct de l'app back-office : utile pour l'onglet et pour
    // « Ajouter à l'écran d'accueil » côté iOS, qui reprend document.title.
    document.title = 'Fleet Hub — Portail'
  }, [])

  const [manualCode, setManualCode] = useState('')
  const [roster, setRoster] = useState(null)
  const [rosterError, setRosterError] = useState('')
  const [selectedDriver, setSelectedDriver] = useState(null)
  const [pin, setPin] = useState('')
  const [pinError, setPinError] = useState('')
  const [verifying, setVerifying] = useState(false)

  const [status, setStatus] = useState(null)
  const [fetchedAt, setFetchedAt] = useState(null)
  const [summary, setSummary] = useState(null)
  const [actionError, setActionError] = useState('')
  const [now, setNow] = useState(Date.now())

  useEffect(() => {
    if (isChauffeur || !urlCode) return
    setRosterError('')
    api.get(`/pointage/roster/${urlCode}`)
      .then((res) => setRoster(res.data))
      .catch(() => setRosterError('Ce lien de portail est invalide. Vérifiez le code avec votre société.'))
  }, [urlCode, isChauffeur])

  const loadStatus = useCallback(() => {
    api.get('/pointage/status')
      .then((res) => {
        setStatus(res.data)
        setFetchedAt(Date.now())
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (!isChauffeur) return
    loadStatus()
    const id = setInterval(loadStatus, 20000)
    return () => clearInterval(id)
  }, [isChauffeur, loadStatus])

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [])

  const pressDigit = async (digit) => {
    if (verifying) return
    const next = pin.length < 4 ? pin + digit : pin
    setPin(next)
    setPinError('')
    if (next.length === 4) {
      setVerifying(true)
      try {
        await login(selectedDriver.loginUsername, next)
      } catch {
        setPinError('Code incorrect. Réessayez.')
        setPin('')
      } finally {
        setVerifying(false)
      }
    }
  }

  const eraseDigit = () => setPin((p) => p.slice(0, -1))

  const runAction = async (action) => {
    setActionError('')
    try {
      const res = await api.post(`/pointage/${action}`)
      setStatus(res.data)
      setFetchedAt(Date.now())
    } catch (err) {
      setActionError(err.response?.data?.message || "L'action a échoué")
    }
  }

  const endShift = async () => {
    setActionError('')
    try {
      const res = await api.post('/pointage/end')
      setSummary(res.data)
      setStatus(null)
    } catch (err) {
      setActionError(err.response?.data?.message || "L'action a échoué")
    }
  }

  const closeSummary = () => {
    setSummary(null)
    loadStatus()
  }

  const changeDriver = async () => {
    await logout()
    setSelectedDriver(null)
    setPin('')
    setStatus(null)
    setSummary(null)
  }

  // ---------- Portail authentifié (chauffeur) ----------
  if (isChauffeur) {
    const elapsedSinceFetch = fetchedAt ? (now - fetchedAt) / 1000 : 0

    return (
      <div className="ptg-page">
        <header className="ptg-topbar">
          <div className="ptg-brand"><span>🚛</span> Fleet Hub</div>
          <button className="ptg-logout" onClick={changeDriver}>Changer de chauffeur</button>
        </header>

        {summary ? (
          <div className="ptg-body">
            <span className="ptg-pill active"><span className="ptg-pill-dot" /> Service terminé</span>
            <div className="card">
              <div className="ptg-recap-row"><span>Service</span><b>{fmtTime(summary.startedAt)} → {fmtTime(summary.endedAt)}</b></div>
              <div className="ptg-recap-row"><span>Conduite totale</span><b>{fmt(summary.totalDrivingSeconds)}</b></div>
              <div className="ptg-recap-row"><span>Pauses</span><b>{summary.pauseCount} ({fmt(summary.totalPauseSeconds)})</b></div>
              <div className="ptg-recap-row">
                <span>Statut 561/2006</span>
                <b style={{ color: summary.compliant ? 'var(--green)' : 'var(--red)' }}>
                  {summary.compliant ? '✓ Conforme' : '⚠ À vérifier'}
                </b>
              </div>
            </div>
            <p className="ptg-hint">Consultable par votre gestionnaire dans Tachographie.</p>
            <div className="ptg-btn-stack">
              <button className="btn btn-primary" onClick={closeSummary}>Fermer</button>
            </div>
          </div>
        ) : !status ? (
          <div className="ptg-body ptg-center"><span className="spinner" /></div>
        ) : status.state === 'IDLE' ? (
          <div className="ptg-body">
            {actionError && <div className="alert alert-error">{actionError}</div>}
            <span className="ptg-pill idle"><span className="ptg-pill-dot" /> Aucun service en cours</span>
            <div className="card">
              <div className="ptg-timer-label">Bonjour {status.driverName.split(' ')[0]}</div>
              <div style={{ fontSize: 15, fontWeight: 700, marginTop: 6 }}>Prêt à prendre la route ?</div>
              <div className="muted" style={{ fontSize: 12, marginTop: 4 }}>
                Ça démarre le minuteur de conduite continue (4h30) et le journal du jour.
              </div>
            </div>
            <div className="ptg-btn-stack">
              <button className="btn btn-primary" onClick={() => runAction('start')}>▶ Démarrer mon service</button>
            </div>
          </div>
        ) : (
          (() => {
            const paused = status.state === 'PAUSE'
            const driving = paused ? status.continuousDrivingSeconds : status.continuousDrivingSeconds + elapsedSinceFetch
            const pause = paused ? status.pauseSeconds + elapsedSinceFetch : 0
            const pct = Math.min(100, (driving / CONTINUOUS_LIMIT_SECONDS) * 100)
            const remaining = CONTINUOUS_LIMIT_SECONDS - driving

            return (
              <div className="ptg-body">
                {actionError && <div className="alert alert-error">{actionError}</div>}
                <span className={`ptg-pill ${paused ? 'pause' : 'active'}`}>
                  <span className="ptg-pill-dot" /> {paused ? 'En pause' : 'En service'}
                </span>
                <div className="card">
                  <div className="ptg-timer-label">{paused ? 'Durée de la pause' : 'Conduite continue'}</div>
                  <div className="ptg-timer">{fmt(paused ? pause : driving)}</div>
                  <div className="ptg-progress-track">
                    <div
                      className={`ptg-progress-fill ${pct > 100 ? 'crit' : pct > 80 ? 'warn' : ''}`}
                      style={{ width: `${Math.min(100, paused ? (pause / (45 * 60)) * 100 : pct)}%`, background: paused ? 'var(--primary-2)' : undefined }}
                    />
                  </div>
                  <div className="ptg-progress-caption">
                    <span>{paused ? 'Seuil réglementaire 45:00' : 'Règle 4h30'}</span>
                    <span>
                      {paused
                        ? (pause >= 45 * 60 ? '✓ Pause suffisante' : `reste ${fmt(45 * 60 - pause)}`)
                        : (remaining < 0 ? 'Pause obligatoire dépassée' : `reste ${fmt(remaining)} avant pause`)}
                    </span>
                  </div>
                </div>

                {status.todayEvents?.length > 0 && (
                  <div className="card ptg-log">
                    <div className="ptg-log-title">Journal du service</div>
                    {status.todayEvents.map((e, i) => (
                      <div className="ptg-log-row" key={i}>
                        <span>{EVENT_LABEL[e.type] || e.type}</span>
                        <span className="muted">{fmtTime(e.occurredAt)}</span>
                      </div>
                    ))}
                  </div>
                )}

                <div className="ptg-btn-stack">
                  {paused ? (
                    <button className="btn btn-primary" onClick={() => runAction('resume')}>▶ Reprendre le service</button>
                  ) : (
                    <button className="btn btn-outline" onClick={() => runAction('pause')}>☕ Faire une pause</button>
                  )}
                  <button className="btn btn-danger" onClick={endShift}>■ Terminer le service</button>
                </div>
              </div>
            )
          })()
        )}
      </div>
    )
  }

  // ---------- Identification (pas encore connecté) ----------
  return (
    <div className="ptg-page">
      <header className="ptg-topbar">
        <div className="ptg-brand"><span>🚛</span> Fleet Hub — Portail</div>
      </header>

      <div className="ptg-body">
        {!urlCode ? (
          <div>
            <p className="ptg-hint" style={{ marginBottom: 14 }}>
              Entrez le code fourni par votre société pour accéder au portail.
            </p>
            <input
              className="ptg-code-input"
              value={manualCode}
              maxLength={12}
              placeholder="CODE SOCIÉTÉ"
              onChange={(e) => setManualCode(e.target.value.toUpperCase())}
            />
            <div className="ptg-btn-stack">
              <button
                className="btn btn-primary"
                disabled={!manualCode.trim()}
                onClick={() => navigate(`/pointage/${manualCode.trim()}`)}
              >
                Continuer
              </button>
            </div>
          </div>
        ) : rosterError ? (
          <div className="alert alert-error">{rosterError}</div>
        ) : !roster ? (
          <div className="ptg-center"><span className="spinner" /></div>
        ) : !selectedDriver ? (
          <>
            <div className="ptg-company-tag">Société</div>
            <div className="ptg-company-name">{roster.companyName}</div>
            <div className="ptg-hint" style={{ marginTop: 10, textAlign: 'left' }}>Qui prend le volant ?</div>
            <div className="ptg-roster">
              {roster.drivers.map((d) => (
                <button key={d.id} className="ptg-roster-row" onClick={() => { setSelectedDriver(d); setPin(''); setPinError('') }}>
                  <span className="ptg-avatar">{d.firstName.charAt(0)}</span>
                  <span>{d.firstName} {d.lastName}</span>
                  <span className="ptg-roster-arrow">→</span>
                </button>
              ))}
            </div>
          </>
        ) : (
          <div className="ptg-center">
            <div className="ptg-avatar ptg-avatar-lg">{selectedDriver.firstName.charAt(0)}</div>
            <div style={{ fontWeight: 700, fontSize: 15, marginTop: 10 }}>{selectedDriver.firstName} {selectedDriver.lastName}</div>
            <div className="ptg-hint">{verifying ? 'Vérification…' : 'Code à 4 chiffres'}</div>
            {pinError && <div className="alert alert-error" style={{ marginTop: 10 }}>{pinError}</div>}
            <div className="ptg-pin-dots">
              {[0, 1, 2, 3].map((i) => <span key={i} className={`ptg-pin-dot ${i < pin.length ? 'filled' : ''}`} />)}
            </div>
            <div className="ptg-pin-pad">
              {['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', '⌫'].map((k, i) =>
                k ? (
                  <button
                    key={i}
                    className="ptg-pin-key"
                    disabled={verifying}
                    onClick={() => (k === '⌫' ? eraseDigit() : pressDigit(k))}
                  >
                    {k}
                  </button>
                ) : <span key={i} className="ptg-pin-key ghost" />
              )}
            </div>
            <div className="ptg-btn-stack" style={{ marginTop: 24 }}>
              <button className="btn btn-outline" onClick={() => setSelectedDriver(null)}>← Changer de chauffeur</button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
