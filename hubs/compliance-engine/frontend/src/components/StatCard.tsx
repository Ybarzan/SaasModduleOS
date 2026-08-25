import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';

interface StatCardProps {
  label: string;
  value: ReactNode;
  icon?: LucideIcon;
  iconColor?: string;
  iconBg?: string;
}

/**
 * Deux variantes du même "carte stat" trouvées dupliquées sur ~22 pages :
 * avec icône (DocumentParser, LandedCostCalculator...) ou centrée sans icône
 * (SharedLinks, Team...). L'icône est optionnelle pour couvrir les deux sans
 * forcer une refonte visuelle des pages qui n'en ont pas.
 */
const StatCard = ({ label, value, icon: Icon, iconColor = 'text-accent', iconBg = 'bg-accent-soft' }: StatCardProps) => {
  if (!Icon) {
    return (
      <div className="bg-surface rounded-none border border-line p-4 text-center">
        <p className="text-2xl font-bold text-ink">{value}</p>
        <p className="text-xs text-ink-soft">{label}</p>
      </div>
    );
  }

  return (
    <div className="bg-surface rounded-none border border-line p-5">
      <div className="flex items-center gap-3">
        <div className={`w-10 h-10 rounded-none ${iconBg} flex items-center justify-center shrink-0`}>
          <Icon size={20} className={iconColor} />
        </div>
        <div>
          <p className="text-sm text-ink-soft">{label}</p>
          <p className="text-2xl font-bold text-ink">{value}</p>
        </div>
      </div>
    </div>
  );
};

export default StatCard;
