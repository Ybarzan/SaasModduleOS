import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  FileText, CalendarClock, Landmark, Banknote, BarChart3, Leaf, FileCheck2,
  Settings as SettingsIcon, LogOut,
} from 'lucide-react';
import { useAuthStore } from '../stores/auth';
import ThemeToggle from '../components/ThemeToggle';

const NAV_ITEMS = [
  { to: '/client-invoices', label: 'Facturation', icon: FileText },
  { to: '/payment-terms', label: 'Conditions', icon: CalendarClock },
  { to: '/supply-chain-finance', label: 'Affacturage', icon: Landmark },
  { to: '/fintech', label: 'Comptes bancaires', icon: Banknote },
  { to: '/financial-reports', label: 'Reporting', icon: BarChart3 },
  { to: '/carbon-dashboard', label: 'Carbone', icon: Leaf },
  { to: '/csrd-report', label: 'CSRD', icon: FileCheck2 },
];

const ADMIN_NAV_ITEMS = [{ to: '/settings', label: 'Paramètres', icon: SettingsIcon }];

// Chrome minimal volontaire : une barre de navigation à plat -- même principe
// que hubs/logistix/src/layouts/AppLayout.tsx.
const AppLayout = () => {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const isAdmin = useAuthStore((s) => s.isAdmin());
  const logout = useAuthStore((s) => s.logout);
  const navItems = isAdmin ? [...NAV_ITEMS, ...ADMIN_NAV_ITEMS] : NAV_ITEMS;

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen flex flex-col bg-bg">
      <header className="border-b border-line bg-surface">
        <div className="max-w-7xl mx-auto px-4 flex items-center justify-between h-14">
          <div className="flex items-center gap-6">
            <span className="text-sm font-semibold text-ink shrink-0">Finance</span>
            <nav className="flex items-center gap-1 overflow-x-auto">
              {navItems.map(({ to, label, icon: Icon }) => (
                <NavLink
                  key={to}
                  to={to}
                  className={({ isActive }) =>
                    `flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors whitespace-nowrap ${
                      isActive ? 'bg-accent-soft text-accent-strong' : 'text-ink-soft hover:text-ink hover:bg-surface-2'
                    }`
                  }
                >
                  <Icon size={14} />
                  <span className="hidden sm:inline">{label}</span>
                </NavLink>
              ))}
            </nav>
          </div>
          <div className="flex items-center gap-3">
            {user?.email && <span className="hidden sm:inline text-xs text-ink-soft">{user.email}</span>}
            <ThemeToggle />
            <button
              onClick={handleLogout}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
              title="Se déconnecter"
            >
              <LogOut size={14} />
              <span className="hidden sm:inline">Déconnexion</span>
            </button>
          </div>
        </div>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  );
};

export default AppLayout;
