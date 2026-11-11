import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ACTIVITIES, rememberActivity, rememberedActivity } from '../fleet'

/**
 * Portail d'entrée : on choisit d'abord son activité (poids lourds, messagerie,
 * transport médical), puis on se connecte ou on crée son compte dans l'espace
 * correspondant.
 */
export default function ActivityPortal() {
  const navigate = useNavigate()
  const location = useLocation()
  const last = rememberedActivity()

  const go = (activity, page) => {
    rememberActivity(activity.slug)
    navigate(`/${page}?activite=${activity.slug}`, { state: location.state })
  }

  return (
    <div className="login-page portal-page">
      <div className="portal">
        <header className="portal-head">
          <div className="login-logo">🧭</div>
          <h1>Fleet Hub</h1>
          <p className="login-sub">Choisissez votre activité pour accéder à votre espace</p>
        </header>

        <div className="portal-grid">
          {ACTIVITIES.map((a) => (
            <article
              key={a.value}
              className={`portal-card activity-${a.slug}` + (last?.slug === a.slug ? ' last' : '')}
            >
              <div className="portal-icon" aria-hidden="true">
                {a.icon}
              </div>
              <h2>{a.label}</h2>
              <p className="portal-hint">{a.hint}</p>
              <p className="portal-tagline">{a.tagline}</p>
              {last?.slug === a.slug && <span className="portal-last">Dernier espace utilisé</span>}
              <div className="portal-actions">
                <button type="button" className="btn btn-primary" onClick={() => go(a, 'login')}>
                  Se connecter
                </button>
                <button type="button" className="btn btn-outline" onClick={() => go(a, 'register')}>
                  Créer un compte
                </button>
              </div>
            </article>
          ))}
        </div>

        <p className="login-hint">
          Chauffeur ? <Link to="/pointage">Accéder au portail chauffeur</Link>
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
