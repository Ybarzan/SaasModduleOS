import { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';
import { LayoutDashboard, LogOut, Menu, X, Calculator } from 'lucide-react';
import ThemeToggle from './ui/ThemeToggle';
import LanguageToggle from './LanguageToggle';

const PUBLIC_LINKS = [
  { to: '/pricing', label: 'Tarifs' },
  { to: '/faq', label: 'FAQ' },
];

/**
 * Marketing-only navbar — rendered on public/marketing pages (Home, Pricing, FAQ,
 * legal pages, public tools). The authenticated app shell (sidebar + groups) lives
 * in AppLayout/Sidebar.tsx instead — see config/navigation.ts.
 */
const Navbar = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const logout = useAuthStore((state) => state.logout);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated());
  const [mobileOpen, setMobileOpen] = useState(false);

  const handleLogout = () => {
    incokalkAPI.auth.logout().catch(() => {});
    logout();
    navigate('/');
    setMobileOpen(false);
  };

  const isActive = (path: string) => location.pathname === path;

  return (
    <nav className="bg-surface/95 backdrop-blur-xl border-b border-line sticky top-0 z-50">
      <div className="container-narrow mx-auto px-4">
        <div className="flex items-center justify-between h-16">
          <Link to="/" className="flex items-center gap-2.5 font-extrabold text-xl group">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-accent to-accent-strong flex items-center justify-center shadow-md shadow-accent/20 group-hover:shadow-lg group-hover:shadow-accent/30 transition-shadow">
              <Calculator size={18} className="text-white" />
            </div>
            <span className="text-ink">Inco<span className="text-accent">Kalk</span></span>
          </Link>

          {/* Desktop nav */}
          <div className="hidden md:flex items-center gap-1">
            {PUBLIC_LINKS.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  isActive(link.to)
                    ? 'text-accent bg-accent-soft'
                    : 'text-ink-soft hover:text-ink hover:bg-surface-2'
                }`}
              >
                {link.label}
              </Link>
            ))}
          </div>

          {/* Desktop right */}
          <div className="hidden md:flex items-center gap-3">
            <LanguageToggle />
            <ThemeToggle />
            {isAuthenticated ? (
              <>
                <Link
                  to="/dashboard"
                  className="px-4 py-2 rounded-lg text-sm font-medium text-ink-soft hover:text-ink hover:bg-surface-2 transition-colors flex items-center gap-1.5"
                >
                  <LayoutDashboard size={16} />
                  Aller à l'app
                </Link>
                <button
                  onClick={handleLogout}
                  className="px-3 py-2 rounded-lg text-sm font-medium text-ink-soft hover:text-accent hover:bg-accent-soft transition-colors flex items-center gap-1.5"
                  title="Déconnexion"
                >
                  <LogOut size={16} />
                </button>
              </>
            ) : (
              <>
                <Link
                  to="/login"
                  className="px-4 py-2 rounded-lg text-sm font-medium text-ink-soft hover:text-ink hover:bg-surface-2 transition-colors"
                >
                  Connexion
                </Link>
                <Link to="/register" className="btn-primary text-sm !px-5 !py-2.5">
                  S'inscrire
                </Link>
              </>
            )}
          </div>

          {/* Mobile toggle */}
          <div className="md:hidden flex items-center gap-2">
            <LanguageToggle />
            <ThemeToggle />
            <button
              onClick={() => setMobileOpen(!mobileOpen)}
              className="p-2 rounded-lg text-ink-soft hover:bg-surface-2 transition-colors"
            >
              {mobileOpen ? <X size={22} /> : <Menu size={22} />}
            </button>
          </div>
        </div>

        {/* Mobile menu */}
        {mobileOpen && (
          <div className="md:hidden border-t border-line py-4 space-y-1 pb-safe animate-fade-in">
            {PUBLIC_LINKS.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-ink hover:bg-surface-2 transition-colors"
                onClick={() => setMobileOpen(false)}
              >
                {link.label}
              </Link>
            ))}

            <div className="border-t border-line pt-3 mt-3 space-y-1">
              {isAuthenticated ? (
                <>
                  <Link
                    to="/dashboard"
                    className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-ink hover:bg-surface-2 transition-colors"
                    onClick={() => setMobileOpen(false)}
                  >
                    <LayoutDashboard size={18} className="text-ink-soft" />
                    Aller à l'app
                  </Link>
                  <button
                    onClick={handleLogout}
                    className="w-full text-left px-3 py-2.5 rounded-lg text-sm font-medium text-accent hover:bg-accent-soft transition-colors flex items-center gap-2"
                  >
                    <LogOut size={16} />
                    Déconnexion
                  </button>
                </>
              ) : (
                <>
                  <Link
                    to="/login"
                    className="block px-3 py-2.5 rounded-lg text-sm font-medium text-ink-soft hover:bg-surface-2 transition-colors"
                    onClick={() => setMobileOpen(false)}
                  >
                    Connexion
                  </Link>
                  <Link
                    to="/register"
                    className="block px-3 py-2.5 rounded-lg text-sm font-semibold text-center btn-primary !py-3"
                    onClick={() => setMobileOpen(false)}
                  >
                    S'inscrire
                  </Link>
                </>
              )}
            </div>
          </div>
        )}
      </div>
    </nav>
  );
};

export default Navbar;
