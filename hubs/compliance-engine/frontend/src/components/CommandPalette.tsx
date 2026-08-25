import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Lock } from 'lucide-react';
import { useAuthStore, type PlanId } from '../stores/auth';
import { useCommandPaletteStore } from '../stores/commandPalette';
import { NAV_GROUPS, STANDALONE_ITEMS, isItemVisible, isItemLocked, type NavItem } from '../config/navigation';

/** Copie volontaire de la même table que Sidebar.tsx (non exportée de là-bas) --
 * dupliquer 4 lignes plutôt que d'exporter et coupler les deux composants. */
const PLAN_DISPLAY_NAME: Record<PlanId, string> = {
  FREE: 'Découverte',
  STARTER: 'Starter',
  PRO: 'Croissance',
  ENTERPRISE: 'Suite',
};

const MAX_RESULTS = 8;

/**
 * Palette de commandes globale (Cmd/Ctrl+K) : avec 88 pages réparties en 7
 * Hubs, retrouver "où est le formulaire EORI" suppose de connaître toute la
 * nav. Réutilise NAV_GROUPS/STANDALONE_ITEMS (source unique déjà utilisée par
 * Sidebar) donc reste automatiquement à jour avec la nav et le gating
 * rôle/plan -- un item verrouillé se comporte comme dans la Sidebar
 * (redirige vers /pricing plutôt que la page réelle).
 *
 * Le champ de recherche vit dans un composant enfant monté uniquement quand
 * isOpen est vrai (au lieu d'un useEffect qui réinitialiserait l'état à
 * l'ouverture) : le montage/démontage remet naturellement query/activeIndex
 * à zéro à chaque ouverture, sans setState dans un effet.
 */
const CommandPalette = () => {
  const isOpen = useCommandPaletteStore((s) => s.isOpen);
  const close = useCommandPaletteStore((s) => s.close);
  const toggle = useCommandPaletteStore((s) => s.toggle);

  useEffect(() => {
    const handler = (e: globalThis.KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        toggle();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [toggle]);

  if (!isOpen) return null;
  return <CommandPaletteDialog onClose={close} />;
};

const CommandPaletteDialog = ({ onClose }: { onClose: () => void }) => {
  const [query, setQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);

  const allItems = useMemo<NavItem[]>(() => {
    const items = [...STANDALONE_ITEMS];
    NAV_GROUPS.forEach((g) => items.push(...g.items));
    return items;
  }, []);

  const results = useMemo(() => {
    const visible = allItems.filter((item) => isItemVisible(item, user?.role));
    const q = query.trim().toLowerCase();
    const matched = q ? visible.filter((item) => item.label.toLowerCase().includes(q)) : visible;
    return matched.slice(0, MAX_RESULTS);
  }, [allItems, user?.role, query]);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const handleQueryChange = (value: string) => {
    setQuery(value);
    setActiveIndex(0);
  };

  const select = (item: NavItem) => {
    const locked = isItemLocked(item, user?.plan);
    navigate(locked ? '/pricing' : item.to);
    onClose();
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      onClose();
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIndex((i) => Math.min(i + 1, results.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIndex((i) => Math.max(i - 1, 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (results[activeIndex]) select(results[activeIndex]);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[100] flex items-start justify-center bg-black/50 pt-[15vh]"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-label="Rechercher une page"
        className="w-full max-w-lg mx-4 bg-surface rounded-none shadow-2xl border border-line overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-2 px-4 py-3 border-b border-line">
          <Search size={16} className="text-ink-soft shrink-0" />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => handleQueryChange(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Rechercher une page ou un outil..."
            className="flex-1 bg-transparent outline-none text-sm text-ink placeholder:text-ink-soft"
          />
          <kbd className="text-[10px] text-ink-soft border border-line rounded px-1.5 py-0.5 shrink-0">Échap</kbd>
        </div>
        <div className="max-h-80 overflow-y-auto py-2">
          {results.length === 0 ? (
            <p className="px-4 py-6 text-sm text-ink-soft text-center">Aucun résultat</p>
          ) : (
            results.map((item, idx) => {
              const Icon = item.icon;
              const locked = isItemLocked(item, user?.plan);
              return (
                <button
                  key={item.to}
                  type="button"
                  onMouseEnter={() => setActiveIndex(idx)}
                  onClick={() => select(item)}
                  className={`w-full flex items-center gap-3 px-4 py-2.5 text-sm text-left transition-colors ${
                    idx === activeIndex ? 'bg-accent-soft text-accent-strong' : 'text-ink hover:bg-surface-2'
                  }`}
                >
                  <Icon size={16} className={locked ? 'text-ink-soft/50 shrink-0' : 'text-ink-soft shrink-0'} />
                  <span className="flex-1 truncate">{item.label}</span>
                  {locked && (
                    <span className="flex items-center gap-1 text-[10px] text-ink-soft shrink-0">
                      <Lock size={11} />
                      {PLAN_DISPLAY_NAME[item.requiredPlan!]}
                    </span>
                  )}
                </button>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};

export default CommandPalette;
