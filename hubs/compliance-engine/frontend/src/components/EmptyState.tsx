import type { LucideIcon } from 'lucide-react';

interface EmptyStateAction {
  label: string;
  onClick: () => void;
  icon?: LucideIcon;
}

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description?: string;
  action?: EmptyStateAction;
}

/**
 * Le même "icône 16x16 + titre + description + bouton" (ou une variante sans
 * bouton) était réimplémenté indépendamment sur des dizaines de pages avec des
 * classes légèrement différentes à chaque fois -- c'est directement pour ça
 * que le bug d'accents cassés (\uXXXX littéral) de la session précédente est
 * passé inaperçu longtemps sur plusieurs pages avant d'être repéré.
 */
const EmptyState = ({ icon: Icon, title, description, action }: EmptyStateProps) => {
  const ActionIcon = action?.icon;
  return (
    <div className="bg-surface rounded-lg shadow-lg p-12 text-center">
      <Icon className="h-16 w-16 text-ink-soft mx-auto mb-4" />
      <h3 className="text-xl font-semibold text-ink mb-2">{title}</h3>
      {description && <p className="text-ink-soft mb-6">{description}</p>}
      {action && (
        <button
          onClick={action.onClick}
          className="bg-accent text-white px-6 py-3 rounded-lg hover:bg-accent-strong transition-colors inline-flex items-center space-x-2"
        >
          {ActionIcon && <ActionIcon size={20} />}
          <span>{action.label}</span>
        </button>
      )}
    </div>
  );
};

export default EmptyState;
