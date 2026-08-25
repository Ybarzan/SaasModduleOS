import { useState } from 'react';
import { Info } from 'lucide-react';

interface InfoTooltipProps {
  text: string;
  className?: string;
}

/**
 * Icône "i" à côté d'un résultat technique (droits de douane, code SH...) —
 * un dirigeant de PME généraliste n'a pas forcément le vocabulaire douanier,
 * et jusqu'ici ces valeurs s'affichaient sans aucune explication.
 * Ouvre au survol/focus clavier, pas de dépendance externe.
 */
const InfoTooltip = ({ text, className = '' }: InfoTooltipProps) => {
  const [open, setOpen] = useState(false);

  return (
    <span
      className={`relative inline-flex items-center ${className}`}
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
    >
      <button
        type="button"
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        className="text-ink-soft hover:text-accent transition-colors"
        aria-label={text}
      >
        <Info size={13} />
      </button>
      {open && (
        <span
          role="tooltip"
          className="absolute z-20 bottom-full left-1/2 -translate-x-1/2 mb-2 w-56 rounded-none border border-line bg-surface px-3 py-2 text-xs font-normal normal-case text-ink-soft shadow-lg"
        >
          {text}
        </span>
      )}
    </span>
  );
};

export default InfoTooltip;
