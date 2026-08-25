import { Link } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';

export interface RelatedTool {
  to: string;
  label: string;
  icon: LucideIcon;
}

/**
 * DocumentParser, DocumentsPage et EmailIntake sont 3 étapes du même flux
 * documentaire (réception → extraction → génération) mais n'avaient aucune
 * navigation entre elles jusqu'ici. Composant volontairement générique : le
 * gating par rôle (chaque page a un niveau d'accès différent) se fait par
 * l'appelant, qui ne passe que les liens réellement accessibles à l'utilisateur.
 */
const RelatedTools = ({ tools }: { tools: RelatedTool[] }) => {
  if (tools.length === 0) return null;
  return (
    <div className="flex flex-wrap items-center gap-2 mb-6 text-sm">
      <span className="text-ink-soft">Outils liés :</span>
      {tools.map((tool) => {
        const Icon = tool.icon;
        return (
          <Link
            key={tool.to}
            to={tool.to}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-none border border-line text-ink-soft hover:border-accent hover:text-accent transition-colors"
          >
            <Icon size={13} />
            {tool.label}
          </Link>
        );
      })}
    </div>
  );
};

export default RelatedTools;
