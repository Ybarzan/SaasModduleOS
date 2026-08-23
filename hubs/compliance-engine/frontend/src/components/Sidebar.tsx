import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ChevronDown, ChevronsLeft, ChevronsRight, Calculator, Lock } from 'lucide-react';
import { useAuthStore, type PlanId } from '../stores/auth';
import { NAV_GROUPS, STANDALONE_ITEMS, isItemVisible, isItemLocked, type NavGroup } from '../config/navigation';

const COLLAPSED_KEY = 'incokalk-sidebar-collapsed';
const GROUPS_KEY = 'incokalk-sidebar-groups';
const SPRING = 'transition-transform duration-300 [transition-timing-function:cubic-bezier(.34,1.56,.64,1)]';

function readExpandedGroups(): Record<string, boolean> {
  try {
    const raw = localStorage.getItem(GROUPS_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

function groupContainingPath(pathname: string): string | null {
  const group = NAV_GROUPS.find((g) => g.items.some((item) => item.to === pathname));
  return group?.id ?? null;
}

const Sidebar = () => {
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(COLLAPSED_KEY) === 'true');
  const [expandedGroups, setExpandedGroups] = useState<Record<string, boolean>>(() => {
    const stored = readExpandedGroups();
    const activeGroup = groupContainingPath(location.pathname);
    return activeGroup ? { ...stored, [activeGroup]: true } : stored;
  });

  useEffect(() => {
    const activeGroup = groupContainingPath(location.pathname);
    if (activeGroup && !expandedGroups[activeGroup]) {
      setExpandedGroups((prev) => ({ ...prev, [activeGroup]: true }));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- ne doit réagir qu'au changement de route
  }, [location.pathname]);

  useEffect(() => {
    localStorage.setItem(GROUPS_KEY, JSON.stringify(expandedGroups));
  }, [expandedGroups]);

  useEffect(() => {
    localStorage.setItem(COLLAPSED_KEY, String(collapsed));
  }, [collapsed]);

  const toggleGroup = (id: string) => {
    setExpandedGroups((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const isActive = (path: string) => location.pathname === path;

  const visibleGroups = NAV_GROUPS.map((group) => ({
    ...group,
    items: group.items.filter((item) => isItemVisible(item, user?.role)),
  })).filter((group) => group.items.length > 0);

  return (
    <aside
      className={`hidden md:flex flex-col shrink-0 sticky top-0 h-screen bg-surface border-r border-line transition-[width] duration-200 ${
        collapsed ? 'w-[72px]' : 'w-[260px]'
      }`}
    >
      <div className="h-16 flex items-center justify-between px-4 border-b border-line shrink-0">
        {!collapsed && (
          <Link to="/" className="flex items-center gap-2.5 text-lg">
            <div className="w-8 h-8 bg-gradient-to-br from-accent to-accent-strong flex items-center justify-center shadow-md shadow-accent/20">
              <Calculator size={16} className="text-white" />
            </div>
            <span className="text-ink font-light">Inco<span className="text-accent font-bold">Kalk</span></span>
          </Link>
        )}
        <button
          onClick={() => setCollapsed(!collapsed)}
          aria-label={collapsed ? 'Déplier la navigation' : 'Replier la navigation'}
          className="p-1.5 text-ink-soft hover:text-ink hover:bg-surface-2 transition-colors"
        >
          {collapsed ? <ChevronsRight size={18} /> : <ChevronsLeft size={18} />}
        </button>
      </div>

      <nav className="flex-1 overflow-y-auto py-4 px-2.5 space-y-1">
        {STANDALONE_ITEMS.filter((item) => isItemVisible(item, user?.role)).map((item) => {
          const Icon = item.icon;
          const active = isActive(item.to);
          return (
            <Link
              key={item.to}
              to={item.to}
              title={collapsed ? item.label : undefined}
              className={`flex items-center gap-3 px-3 py-2.5 text-sm font-semibold ${SPRING} hover:translate-x-0.5 ${
                item.highlight
                  ? active
                    ? 'bg-accent text-white shadow-md shadow-accent/25'
                    : 'bg-accent-soft text-accent hover:bg-accent-soft'
                  : active
                    ? 'bg-surface-2 text-ink'
                    : 'text-ink-soft hover:bg-surface-2 hover:text-ink'
              } ${collapsed ? 'justify-center hover:translate-x-0' : ''}`}
            >
              <Icon size={18} />
              {!collapsed && (
                <span className="truncate flex items-center gap-1.5">
                  {active && (
                    <span className={item.highlight ? 'text-white' : 'text-accent'} aria-hidden="true">&gt;</span>
                  )}
                  {item.label}
                </span>
              )}
            </Link>
          );
        })}

        <div className="pt-3 mt-2 border-t border-line space-y-1">
          {visibleGroups.map((group) => (
            <SidebarGroup
              key={group.id}
              group={group}
              collapsed={collapsed}
              expanded={expandedGroups[group.id] ?? false}
              onToggle={() => toggleGroup(group.id)}
              isActive={isActive}
              userPlan={user?.plan}
            />
          ))}
        </div>
      </nav>
    </aside>
  );
};

/** Nom commercial affiché du plan qui débloque un item verrouillé (voir BillingService.getPlans). */
const PLAN_DISPLAY_NAME: Record<PlanId, string> = {
  FREE: 'Découverte',
  STARTER: 'Starter',
  PRO: 'Croissance',
  ENTERPRISE: 'Suite',
};

function SidebarGroup({
  group,
  collapsed,
  expanded,
  onToggle,
  isActive,
  userPlan,
}: {
  group: NavGroup;
  collapsed: boolean;
  expanded: boolean;
  onToggle: () => void;
  isActive: (path: string) => boolean;
  userPlan?: PlanId;
}) {
  const GroupIcon = group.icon;
  const hasActive = group.items.some((item) => isActive(item.to));

  return (
    <div>
      <button
        onClick={onToggle}
        title={collapsed ? group.label : undefined}
        className={`w-full flex items-center gap-3 px-3 py-2.5 text-sm font-medium transition-colors ${
          hasActive ? 'text-accent' : 'text-ink-soft hover:bg-surface-2 hover:text-ink'
        } ${collapsed ? 'justify-center' : 'justify-between'}`}
      >
        <span className="flex items-center gap-3 min-w-0">
          <GroupIcon size={18} className="shrink-0" />
          {!collapsed && <span className="truncate">{group.label}</span>}
        </span>
        {!collapsed && (
          <ChevronDown size={14} className={`shrink-0 transition-transform duration-200 ${expanded ? 'rotate-180' : ''}`} />
        )}
      </button>
      {expanded && !collapsed && (
        <div className="pl-4 mt-1 space-y-0.5">
          {group.items.map((item) => {
            const Icon = item.icon;
            const active = isActive(item.to);
            const locked = isItemLocked(item, userPlan);
            if (locked) {
              return (
                <Link
                  key={item.to}
                  to="/pricing"
                  title={`Disponible avec le plan ${PLAN_DISPLAY_NAME[item.requiredPlan!]}`}
                  className="flex items-center gap-3 px-3 py-2 text-sm text-ink-soft/50 hover:text-ink-soft hover:bg-surface-2 transition-colors"
                >
                  <Icon size={15} className="text-ink-soft/50" />
                  <span className="truncate flex-1">{item.label}</span>
                  <Lock size={12} className="shrink-0" />
                </Link>
              );
            }
            return (
              <Link
                key={item.to}
                to={item.to}
                className={`flex items-center gap-3 px-3 py-2 text-sm ${SPRING} hover:translate-x-0.5 ${
                  active
                    ? 'text-accent bg-accent-soft font-semibold'
                    : 'text-ink-soft hover:text-ink hover:bg-surface-2'
                }`}
              >
                <Icon size={15} className={active ? 'text-accent' : 'text-ink-soft'} />
                <span className="truncate flex items-center gap-1.5">
                  {active && <span aria-hidden="true">&gt;</span>}
                  {item.label}
                </span>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default Sidebar;
