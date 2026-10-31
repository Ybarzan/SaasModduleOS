import { AlertTriangle } from 'lucide-react';

/**
 * Affiché quand l'API renvoie rateAvailable=false : aucun taux fiable pour ce code/cette lane.
 * On n'affiche jamais 0 % ni un taux moyen à la place — l'utilisateur doit savoir qu'il faut vérifier.
 */
const DutyUnavailable = ({ notes }: { notes?: string }) => (
  <div role="status" className="relative bg-warning/10 border border-warning/40 rounded-none p-6 flex items-start gap-3">
    <AlertTriangle size={20} className="text-warning mt-0.5 flex-shrink-0" aria-hidden="true" />
    <div>
      <h2 className="text-sm font-semibold text-ink uppercase tracking-wider mb-1">Droits de douane non disponibles</h2>
      <p className="text-sm text-ink-soft">
        {notes || 'Données tarifaires non disponibles — rapprochement transitaire requis.'}
      </p>
    </div>
  </div>
);

export default DutyUnavailable;
