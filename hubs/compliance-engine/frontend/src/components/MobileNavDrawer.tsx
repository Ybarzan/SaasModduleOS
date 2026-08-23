import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Menu, X, ChevronDown, LogOut, Lock } from 'lucide-react';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';
import { NAV_GROUPS, STANDALONE_ITEMS, isItemVisible, isItemLocked } from '../config/navigation';
import RoleBadge from './RoleBadge';
import ThemeToggle from './ui/ThemeToggle';

/** Nom commercial affiché du plan qui débloque un item verrouillé (voir BillingService.getPlans). */
const PLAN_DISPLAY_NAME: Record<string, string> = {
  FREE: 'Découverte',
  STARTER: 'Starter',
  PRO: 'Croissance',
  ENTERPRISE: 'Suite',
};

const MobileNavDrawer = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const logout = useAuthStore((s) => s.logout);
  const user = useAuthStore((s) => s.user);
  const [open, setOpen] = useState(false);
  const [openGroups, setOpenGroups] = useState<Record<string, boolean>>({});

  const isActive = (path: string) => location.pathname === path;
  const toggleGroup = (id: string) => setOpenGroups((prev) => ({ ...prev, [id]: !prev[id] }));

  const visibleGroups = NAV_GROUPS.map((group) => ({
    ...group,
    items: group.items.filter((item) => isItemVisible(item, user?.role)),
  })).filter((group) => group.items.length > 0);

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        aria-label="Ouvrir le menu complet"
        className="md:hidden fixed top-3 right-3 z-40 p-2.5 rounded-xl bg-surface/95 backdrop-blur-lg border border-line shadow-md text-ink-soft"
      >
        <Menu size={20} />
      </button>

      {open && (
        <div className="md:hidden fixed inset-0 z-[70] bg-surface flex flex-col animate-fade-in">
          <div className="h-16 flex items-center justify-between px-4 border-b border-line shrink-0">
            <span className="font-extrabold text-lg text-ink">
              Inco<span className="text-accent">Kalk</span>
            </span>
            <div className="flex items-center gap-2">
              <ThemeToggle />
              <button
                onClick={() => setOpen(false)}
                aria-label="Fermer le menu"
                className="p-2 rounded-lg text-ink-soft hover:bg-surface-2"
              >
                <X size={22} />
              </button>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto px-3 py-4 space-y-1 pb-safe">
            {STANDALONE_ITEMS.filter((item) => isItemVisible(item, user?.role)).map((item) => {
              const Icon = item.icon;
              const active = isActive(item.to);
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  onClick={() => setOpen(false)}
                  className={`flex items-center gap-3 px-3 py-3 rounded-xl text-sm font-semibold ${
                    item.highlight
                      ? 'bg-accent-soft text-accent'
                      : active
                        ? 'bg-surface-2 text-ink'
                        : 'text-ink'
                  }`}
                >
                  <Icon size={18} />
                  {item.label}
                </Link>
              );
            })}

            <div className="pt-3 mt-2 border-t border-line space-y-1">
              {visibleGroups.map((group) => {
                const GroupIcon = group.icon;
                const expanded = openGroups[group.id] ?? false;
                return (
                  <div key={group.id}>
                    <button
                      onClick={() => toggleGroup(group.id)}
                      className="w-full flex items-center justify-between px-3 py-3 rounded-xl text-sm font-medium text-ink hover:bg-surface-2"
                    >
                      <span className="flex items-center gap-3">
                        <GroupIcon size={18} className="text-ink-soft" />
                        {group.label}
                      </span>
                      <ChevronDown size={16} className={`transition-transform duration-200 ${expanded ? 'rotate-180' : ''}`} />
                    </button>
                    {expanded && (
                      <div className="pl-4 space-y-0.5 mt-1">
                        {group.items.map((item) => {
                          const Icon = item.icon;
                          const locked = isItemLocked(item, user?.plan);
                          if (locked) {
                            return (
                              <Link
                                key={item.to}
                                to="/pricing"
                                onClick={() => setOpen(false)}
                                title={`Disponible avec le plan ${PLAN_DISPLAY_NAME[item.requiredPlan!]}`}
                                className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm text-ink-soft/50"
                              >
                                <Icon size={16} className="text-ink-soft/50" />
                                <span className="flex-1">{item.label}</span>
                                <Lock size={12} />
                              </Link>
                            );
                          }
                          return (
                            <Link
                              key={item.to}
                              to={item.to}
                              onClick={() => setOpen(false)}
                              className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm ${
                                isActive(item.to) ? 'text-accent font-semibold bg-accent-soft' : 'text-ink-soft'
                              }`}
                            >
                              <Icon size={16} className="text-ink-soft" />
                              {item.label}
                            </Link>
                          );
                        })}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>

            <div className="border-t border-line pt-3 mt-3">
              <div className="px-3 py-2 text-sm text-ink-soft flex items-center gap-2">
                {user?.firstName} {user?.lastName}
                {user?.role && <RoleBadge role={user.role} size="sm" />}
              </div>
              <button
                onClick={() => {
                  incokalkAPI.auth.logout().catch(() => {});
                  logout();
                  navigate('/');
                  setOpen(false);
                }}
                className="w-full text-left px-3 py-3 rounded-xl text-sm font-medium text-accent hover:bg-accent-soft flex items-center gap-2"
              >
                <LogOut size={16} />
                Déconnexion
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default MobileNavDrawer;
