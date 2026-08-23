import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../context/ThemeContext'

const stroke = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round',
  strokeLinejoin: 'round'
}

function Icon({ d, size = 18 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <g {...stroke}>{d}</g>
    </svg>
  )
}

const icons = {
  dashboard: <Icon d={<><rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" /><rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" /></>} />,
  drivers: <Icon d={<><circle cx="12" cy="8" r="3.6" /><path d="M5 20c0-3.3 3.1-5.5 7-5.5s7 2.2 7 5.5" /></>} />,
  trucks: <Icon d={<><path d="M3 6h11v10H3z" /><path d="M14 9h4l3 3v4h-7z" /><circle cx="7" cy="18.5" r="1.8" /><circle cx="17.5" cy="18.5" r="1.8" /></>} />,
  data: <Icon d={<><path d="M4 20h4l12-12-4-4L4 16z" /><path d="M13 6l4 4" /></>} />,
  map: <Icon d={<><path d="M12 21s7-6.1 7-11a7 7 0 1 0-14 0c0 4.9 7 11 7 11z" /><circle cx="12" cy="10" r="2.6" /></>} />,
  tacho: <Icon d={<><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2.2" /><path d="M6.6 18.2l-1.1 1.1" /></>} />,
  alerts: <Icon d={<><path d="M12 4c-3.6 0-6 2.6-6 6.5V15l-1.5 2.5h15L18 15v-4.5C18 6.6 15.6 4 12 4z" /><path d="M9.8 20.5a2.4 2.4 0 0 0 4.4 0" /></>} />,
  billing: <Icon d={<><rect x="2.5" y="5" width="19" height="14" rx="2.5" /><path d="M2.5 10h19" /></>} />,
  users: <Icon d={<><circle cx="9" cy="8.5" r="3.4" /><path d="M3 20c0-3.2 2.7-5.3 6-5.3s6 2.1 6 5.3" /><path d="M16 5.6a3.4 3.4 0 0 1 0 5.8M18.5 15.3c2 .8 3.5 2.4 3.5 4.7" /></>} />,
  integrations: <Icon d={<><path d="M9 6h6v6H9zM9 12h6v6H9zM9 9h6" /><path d="M9 15h6" /></>} />,
  import: <Icon d={<><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="17 8 12 3 7 8" /><line x1="12" y1="3" x2="12" y2="15" /></>} />,
  rgpd: <Icon d={<><path d="M12 3l7 3v5c0 4.5-3 8.2-7 9-4-.8-7-4.5-7-9V6z" /></>} />,
  admin: <Icon d={<><circle cx="12" cy="12" r="3.2" /><path d="M12 2.8v2.4M12 18.8v2.4M4.6 5.6l2 1.4M17.4 17l2 1.4M2.8 12h2.4M18.8 12h2.4M4.6 18.4l2-1.4M17.4 7l2-1.4" /></>} />,
  settings: <Icon d={<><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" /></>} />
}

const navItems = [
  { to: '/', label: 'Tableau de bord', icon: 'dashboard', end: true },
  { to: '/drivers', label: 'Chauffeurs', icon: 'drivers', end: false },
  { to: '/tachographie', label: 'Tachographie', icon: 'tacho', end: false },
  { to: '/trucks', label: 'Camions', icon: 'trucks', end: false },
  { to: '/data', label: 'Saisie', icon: 'data', end: false },
  { to: '/import', label: 'Import fichiers', icon: 'import', end: false },
  { to: '/map', label: 'Carte temps réel', icon: 'map', end: false },
  { to: '/notifications', label: 'Alertes', icon: 'alerts', end: false },
  { to: '/billing', label: 'Abonnement', icon: 'billing', end: false },
  { to: '/users', label: 'Utilisateurs', icon: 'users', end: false, adminOnly: true },
  { to: '/integrations', label: 'Intégrations', icon: 'integrations', end: false, adminOnly: true },
  { to: '/rgpd', label: 'Mes données', icon: 'rgpd', end: false, adminOnly: true },
  { to: '/admin', label: 'Administration', icon: 'admin', end: false, saasOnly: true },
  { to: '/settings', label: 'Paramètres', icon: 'settings', end: false }
]

const visibleNav = (user) =>
  navItems.filter(
    (item) =>
      (!item.saasOnly || user?.role === 'SAAS_ADMIN') &&
      (!item.adminOnly || user?.role === 'ADMIN')
  )

const roleLabel = (role) =>
  role === 'SAAS_ADMIN' ? 'Opérateur plateforme' : role === 'ADMIN' ? 'Administrateur' : 'Gestionnaire'

const tap = (fn) => (e) => {
  window.__fhHaptics?.light()
  fn(e)
}

export default function Layout() {
  const { user, logout } = useAuth()
  const { theme, toggleTheme } = useTheme()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)

  const handleLogout = () => {
    window.__fhHaptics?.medium()
    logout()
    navigate('/login')
  }

  const closeMenu = () => setMenuOpen(false)

  return (
    <div className={'layout' + (menuOpen ? ' menu-open' : '')}>
      <header className="mobile-topbar">
        <button
          className="hamburger"
          onClick={tap(() => setMenuOpen(true))}
          aria-label="Ouvrir le menu"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
            <path d="M3 6h18M3 12h18M3 18h18" />
          </svg>
        </button>
        <div className="mobile-brand">
          <span>🚛</span> Fleet Hub
        </div>
        <button className="theme-toggle theme-toggle-mobile" onClick={tap(toggleTheme)} aria-label={theme === 'dark' ? 'Passer en mode clair' : 'Passer en mode sombre'}>
          {theme === 'dark' ? (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
              <circle cx="12" cy="12" r="4.5" /><path d="M12 2.5v2M12 19.5v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2.5 12h2M19.5 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
            </svg>
          ) : (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
            </svg>
          )}
        </button>
      </header>
      <div className="sidebar-overlay" onClick={closeMenu} />

      <aside className={'sidebar' + (menuOpen ? ' open' : '')}>
        <div className="logo">
          <span className="logo-icon">🚛</span>
          <div>
            <h1>Fleet Hub</h1>
            <p>{user?.companyName || 'SYSTÈME / GESTION FLOTTE'}</p>
          </div>
          <button
            className="sidebar-close"
            onClick={closeMenu}
            aria-label="Fermer le menu"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>
        <nav>
          {visibleNav(user).map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              onClick={closeMenu}
              className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}
            >
              <span>{icons[item.icon]}</span> {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <div className="user-chip">
            <div className="avatar">{user?.displayName?.charAt(0) || 'G'}</div>
            <div className="user-info">
              <strong>{user?.displayName}</strong>
              <span>{roleLabel(user?.role)}</span>
            </div>
          </div>
          <button className="theme-toggle" onClick={tap(toggleTheme)} aria-label={theme === 'dark' ? 'Passer en mode clair' : 'Passer en mode sombre'}>
            <span className="theme-toggle-icon">
              {theme === 'dark' ? (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                  <circle cx="12" cy="12" r="4.5" /><path d="M12 2.5v2M12 19.5v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2.5 12h2M19.5 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
                </svg>
              ) : (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                  <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
                </svg>
              )}
            </span>
            <span>{theme === 'dark' ? 'Mode clair' : 'Mode sombre'}</span>
          </button>
          <button className="btn btn-outline btn-block" onClick={handleLogout}>
            Déconnexion
          </button>
        </div>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
