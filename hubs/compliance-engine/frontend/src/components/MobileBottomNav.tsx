import { Link, useLocation } from 'react-router-dom';
import { Calculator, Truck, Package, Search, Home } from 'lucide-react';
import { useIsMobile } from '../hooks/useMediaQuery';

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Accueil', icon: Home },
  { to: '/simulation', label: 'Simuler', icon: Calculator },
  { to: '/shipments', label: 'Expéditions', icon: Package },
  { to: '/carriers', label: 'Transport', icon: Truck },
  { to: '/quotes', label: 'Devis', icon: Search },
];

const MobileBottomNav = () => {
  const location = useLocation();
  const isMobile = useIsMobile();

  if (!isMobile) return null;

  return (
    <nav className="fixed bottom-0 left-0 right-0 bg-surface/95 backdrop-blur-lg border-t border-line z-50 safe-area-inset-bottom shadow-[0_-4px_20px_rgba(0,0,0,0.06)]">
      <div className="flex items-center justify-around h-16">
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          const isActive = location.pathname === item.to;
          return (
            <Link
              key={item.to}
              to={item.to}
              className={`flex flex-col items-center justify-center w-full h-full transition-all duration-200 ${
                isActive
                  ? 'text-accent'
                  : 'text-ink-soft hover:text-ink'
              }`}
            >
              <div className={`relative ${isActive ? '-translate-y-0.5' : ''}`}>
                <Icon size={20} strokeWidth={isActive ? 2.5 : 1.5} />
                {isActive && (
                  <div className="absolute -bottom-1.5 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-accent" />
                )}
              </div>
              <span className={`text-[10px] mt-1 ${isActive ? 'font-bold' : 'font-medium'}`}>
                {item.label}
              </span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
};

export default MobileBottomNav;
