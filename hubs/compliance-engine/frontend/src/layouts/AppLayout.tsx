import { useEffect, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { LogOut, Search, HelpCircle } from 'lucide-react';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';
import { useCommandPaletteStore } from '../stores/commandPalette';
import { useOnboardingStore } from '../stores/onboarding';
import { getPageLabel } from '../config/navigation';
import Sidebar from '../components/Sidebar';
import MobileNavDrawer from '../components/MobileNavDrawer';
import MobileBottomNav from '../components/MobileBottomNav';
import NotificationBell from '../components/NotificationBell';
import RoleBadge from '../components/RoleBadge';
import ThemeToggle from '../components/ui/ThemeToggle';
import CommandPalette from '../components/CommandPalette';
import OnboardingModal from '../components/OnboardingModal';

const AppLayout = ({ children }: { children: ReactNode }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const openCommandPalette = useCommandPaletteStore((s) => s.open);
  const openOnboarding = useOnboardingStore((s) => s.open);

  const pageLabel = getPageLabel(location.pathname);

  // Lecture ponctuelle du store au montage (pas un abonnement réactif) --
  // on ne veut déclencher l'ouverture qu'une fois, jamais en réaction à un
  // changement ultérieur de hasSeenOnboarding (ex: après un `close()`).
  useEffect(() => {
    if (!useOnboardingStore.getState().hasSeenOnboarding) {
      useOnboardingStore.getState().open();
    }
  }, []);

  const handleLogout = () => {
    incokalkAPI.auth.logout().catch(() => {});
    logout();
    navigate('/');
  };

  return (
    <div className="flex min-h-screen bg-bg">
      <Sidebar />
      <MobileNavDrawer />

      <div className="flex-1 flex flex-col min-w-0">
        <header className="h-16 shrink-0 bg-surface/95 backdrop-blur-xl border-b border-line flex items-center justify-between px-4 md:px-6 sticky top-0 z-30">
          <h1 className="text-base font-bold text-ink truncate">{pageLabel ?? 'IncoKalk'}</h1>
          <div className="flex items-center gap-3">
            <button
              onClick={openCommandPalette}
              title="Rechercher (Ctrl+K)"
              className="hidden sm:flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm text-ink-soft border border-line hover:border-accent hover:text-accent transition-colors"
            >
              <Search size={14} />
              <span className="hidden md:inline">Rechercher</span>
              <kbd className="text-[10px] border border-line rounded px-1 py-0.5">Ctrl K</kbd>
            </button>
            <button
              onClick={openOnboarding}
              title="Revoir le guide de démarrage"
              className="p-2 rounded-lg text-ink-soft hover:text-accent hover:bg-accent-soft transition-colors"
            >
              <HelpCircle size={16} />
            </button>
            <ThemeToggle />
            <div className="hidden md:flex items-center gap-3">
              <NotificationBell />
              <div className="flex items-center gap-2 pl-3 border-l border-line">
                <span className="text-sm text-ink-soft font-medium">
                  {user?.firstName} {user?.lastName}
                </span>
                {user?.role && <RoleBadge role={user.role} size="sm" />}
              </div>
              <button
                onClick={handleLogout}
                title="Déconnexion"
                className="p-2 rounded-lg text-ink-soft hover:text-accent hover:bg-accent-soft transition-colors"
              >
                <LogOut size={16} />
              </button>
            </div>
          </div>
        </header>

        <main className="flex-1 pb-20 md:pb-0">{children}</main>
      </div>

      <MobileBottomNav />
      <CommandPalette />
      <OnboardingModal />
    </div>
  );
};

export default AppLayout;
